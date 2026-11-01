package co.wethinkcode.healthsafe.infrastructure.http;

import co.wethinkcode.healthsafe.application.StaffingDataClient;
import co.wethinkcode.healthsafe.application.StaffingServiceException;
import co.wethinkcode.healthsafe.domain.WardSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Handles HTTP communication with the services from which Staffing Service
 * needs data.
 *
 * This class has two responsibilities:
 * 1. Load and maintain a local snapshot of the cleaned ward catalogue.
 * 2. Retrieve the current emergency level from Alert Level Service.
 *
 * It implements StaffingDataClient so the application layer can use it
 * without depending directly on Java's HTTP client or Jackson.
 */
public class HttpStaffingDataClient implements StaffingDataClient {

    private final String ingestionWardsUrl;
    private final String alertLevelServiceUrl;

    // Reuse the HTTP client instead of creating a new one for every request.
    private final HttpClient httpClient;

    // Jackson converts the JSON responses into objects that Java can use.
    private final ObjectMapper objectMapper;

    /*
     * Stores the ward catalogue locally.
     *
     * The volatile reference ensures that other request threads see the
     * latest complete snapshot when a new catalogue is loaded.
     *
     * Map.of() starts with an empty, immutable map.
     */
    private volatile Map<String, WardSummary> wardCache = Map.of();

    public HttpStaffingDataClient(
            String ingestionWardsUrl,
            String alertLevelServiceUrl
    ) {
        this.ingestionWardsUrl = ingestionWardsUrl;
        this.alertLevelServiceUrl = alertLevelServiceUrl;

        // Set a connection timeout so an unavailable service cannot
        // cause the calling thread to wait indefinitely while connecting.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        this.objectMapper = new ObjectMapper();
    }

    /**
     * Loads the cleaned ward catalogue from Ingestion Service.
     *
     * This is called when Staffing Service starts. It builds a new map
     * separately and only replaces the current cache after the complete
     * response has been validated.
     *
     * Staffing Service can then validate wards locally without making
     * a synchronous request to Ward Service for every schedule.
     */
    public void loadWardCatalogue() {

        HttpResponse<String> response =
                get(ingestionWardsUrl, "ingestion-service");

        // A non-200 response means the source data could not be loaded.
        if (response.statusCode() != 200) {
            throw new StaffingServiceException(
                    503,
                    "ingestion-service returned HTTP " + response.statusCode()
            );
        }

        try {
            JsonNode wardsJson = objectMapper.readTree(response.body());

            // The ingestion contract requires a JSON array of ward records.
            if (!wardsJson.isArray()) {
                throw new StaffingServiceException(
                        502, "ingestion-service did not return a ward array"
                );
            }

            // Build a temporary map so incomplete data never replaces
            // a previously loaded, valid catalogue.
            Map<String, WardSummary> refreshed = new HashMap<>();

            for (JsonNode wardJson : wardsJson) {

                // Normalize IDs so "w-05" and "W-05" use the same cache key.
                String wardId = wardJson.path("wardId").asText("")
                        .trim().toUpperCase(Locale.ROOT);

                String department =
                        wardJson.path("department").asText("").trim();

                // Records without the minimum information needed for
                // scheduling are not added to the local catalogue.
                if (wardId.isBlank() || department.isBlank()) {
                    continue;
                }

                WardSummary ward = new WardSummary(wardId, department);

                // Ingestion Service should already remove duplicates.
                // Reject duplicates here rather than silently replacing
                // one ward with another.
                if (refreshed.putIfAbsent(wardId, ward) != null) {
                    throw new StaffingServiceException(
                            502,
                            "Duplicate ward ID from ingestion-service: " + wardId
                    );
                }
            }

            // An empty catalogue is not useful for schedule generation.
            if (refreshed.isEmpty()) {
                throw new StaffingServiceException(
                        502, "ingestion-service returned no usable wards"
                );
            }

            // Publish the completed snapshot in one operation.
            // Map.copyOf prevents callers from modifying the cached map.
            wardCache = Map.copyOf(refreshed);

            System.out.printf(
                    "[staffing-service] loaded %d wards from ingestion%n",
                    wardCache.size()
            );

        } catch (IOException e) {
            // The response was received, but its JSON could not be parsed.
            throw new StaffingServiceException(
                    502, "Could not parse ingestion-service response", e
            );
        }
    }

    /**
     * Looks up a ward in the local snapshot.
     *
     * No HTTP request is made here. This removes the runtime dependency
     * between Staffing Service and Ward Service.
     */
    @Override
    public WardSummary findWard(String wardId) {

        // Apply the same ID normalization used when loading the catalogue.
        WardSummary ward = wardCache.get(
                wardId.trim().toUpperCase(Locale.ROOT)
        );

        if (ward == null) {
            throw new StaffingServiceException(
                    404, "Unknown ward: " + wardId
            );
        }

        return ward;
    }

    /**
     * Retrieves the latest hospital emergency level.
     *
     * Unlike ward details, the alert level can change during operation,
     * so Staffing Service reads it when calculating each schedule.
     */
    @Override
    public int getCurrentAlertLevel() {

        HttpResponse<String> response =
                get(alertLevelServiceUrl, "alert-level-service");

        if (response.statusCode() != 200) {
            throw new StaffingServiceException(
                    503,
                    "alert-level-service returned HTTP " + response.statusCode()
            );
        }

        try {
            JsonNode body = objectMapper.readTree(response.body());

            // Check that the expected field exists and contains an integer.
            if (!body.has("level") || !body.get("level").canConvertToInt()) {
                throw new StaffingServiceException(
                        502, "alert-level-service returned an invalid level"
                );
            }

            int level = body.get("level").asInt();

            // Validate the upstream value as well. A successful HTTP response
            // does not guarantee that the response contains valid domain data.
            if (level < 0 || level > 8) {
                throw new StaffingServiceException(
                        502, "alert-level-service returned a level outside 0-8"
                );
            }

            return level;

        } catch (IOException e) {
            throw new StaffingServiceException(
                    502, "Could not parse alert-level-service response", e
            );
        }
    }

    /**
     * Executes a GET request and handles common communication failures.
     *
     * The service name is included in errors so the caller can identify
     * which dependency was unavailable.
     */
    private HttpResponse<String> get(String url, String serviceName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

        } catch (InterruptedException e) {
            // Restore the interrupted flag so the application does not
            // accidentally swallow a cancellation or shutdown signal.
            Thread.currentThread().interrupt();

            throw new StaffingServiceException(
                    503, serviceName + " request was interrupted", e
            );

        } catch (IOException | IllegalArgumentException e) {
            // Covers connection failures, timeouts and invalid request URIs.
            throw new StaffingServiceException(
                    503, serviceName + " is unavailable", e
            );
        }
    }
}