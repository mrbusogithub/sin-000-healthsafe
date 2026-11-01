package co.wethinkcode.healthsafe.infrastructure.http;

import co.wethinkcode.healthsafe.domain.Ward;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * HTTP adapter used to retrieve cleaned ward records from Ingestion Service.
 *
 * It is part of the infrastructure layer because it knows how to make
 * HTTP requests and convert JSON responses into Ward objects.
 *
 * WardCatalogService uses this class rather than implementing HTTP
 * communication itself.
 */
public class IngestionWardClient {

    // The endpoint is configurable so it can differ between environments.
    private final String endpoint;

    // Reuse the HTTP client for requests to Ingestion Service.
    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

    // Converts the JSON array returned by Ingestion Service into Java objects.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestionWardClient(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Ingestion URL is required");
        }

        // Avoid creating a URL with two slashes when joining paths.
        this.endpoint = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
    }

    /**
     * Requests the cleaned ward catalogue from Ingestion Service.
     *
     * @return a list of cleaned Ward objects
     * @throws IllegalStateException when the service is unavailable,
     *         returns a non-success response or provides invalid JSON
     */
    public List<Ward> fetchWards() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Ward Service must not cache an error response as ward data.
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "ingestion-service returned HTTP " + response.statusCode()
                );
            }

            /*
             * TypeReference preserves the generic List<Ward> information
             * required by Jackson when reading the JSON array.
             */
            return objectMapper.readValue(
                    response.body(),
                    new TypeReference<List<Ward>>() {}
            );

        } catch (InterruptedException e) {
            // Preserve the interrupted status for proper cancellation handling.
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Ingestion request was interrupted", e
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not retrieve wards from ingestion-service", e
            );
        }
    }
}