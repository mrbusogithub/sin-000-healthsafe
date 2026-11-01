package co.wethinkcode.healthsafe.application;

import co.wethinkcode.healthsafe.domain.Ward;
import co.wethinkcode.healthsafe.infrastructure.http.IngestionWardClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Manages the ward catalogue used by Ward Service.
 *
 * Its responsibilities are to:
 * - Retrieve wards through IngestionWardClient.
 * - Normalize ward IDs used as cache keys.
 * - Reject duplicate IDs from the upstream response.
 * - Replace the current catalogue after a successful refresh.
 * - Provide lookup methods for the API layer.
 *
 * It does not contain HTTP route definitions or JMS code.
 */
public class WardCatalogService {

    private final IngestionWardClient ingestionClient;

    /*
     * The volatile reference allows request threads to see a newly
     * published catalogue snapshot without locking every read operation.
     *
     * The map itself is replaced rather than modified after publication.
     */
    private volatile Map<String, Ward> wards = Map.of();

    public WardCatalogService(IngestionWardClient ingestionClient) {
        this.ingestionClient = ingestionClient;
    }

    /**
     * Refreshes the ward catalogue from Ingestion Service.
     *
     * A temporary map is built first. If retrieval or validation fails,
     * the existing catalogue remains untouched.
     *
     * @return number of wards in the new catalogue
     */
    public synchronized int refresh() {

        // Retrieve the latest cleaned records from the source service.
        List<Ward> loadedWards = ingestionClient.fetchWards();

        // TreeMap gives predictable key ordering while the snapshot is built.
        Map<String, Ward> refreshed = new TreeMap<>();

        for (Ward ward : loadedWards) {

            // Ignore records that cannot be identified as a ward.
            if (ward == null || ward.getWardId() == null
                    || ward.getWardId().isBlank()) {
                continue;
            }

            // Use a normalized ID consistently for both storage and lookup.
            String id = ward.getWardId().trim().toUpperCase();

            // Keep the object ID consistent with the normalized cache key.
            ward.setWardId(id);

            /*
             * Ingestion Service is responsible for deduplication.
             * If it sends duplicate IDs, fail the refresh rather than
             * silently overwriting one record with another.
             */
            if (refreshed.putIfAbsent(id, ward) != null) {
                throw new IllegalStateException(
                        "Duplicate ward ID received from ingestion: " + id
                );
            }
        }

        /*
         * Replace the cache only after the complete response has been checked.
         * Map.copyOf prevents accidental modification of the published snapshot.
         */
        wards = Map.copyOf(refreshed);

        System.out.printf(
                "[ward-service] loaded %d wards from ingestion%n",
                wards.size()
        );

        return wards.size();
    }

    /**
     * Returns all cached wards in ward-ID order.
     */
    public List<Ward> findAll() {
        return wards.values()
                .stream()
                .sorted(Comparator.comparing(Ward::getWardId))
                .toList();
    }

    /**
     * Finds a ward by its ID.
     *
     * Returns Optional.empty() when the supplied ID is blank or unknown,
     * allowing the API layer to decide how to represent that result in HTTP.
     */
    public Optional<Ward> findById(String rawWardId) {
        if (rawWardId == null || rawWardId.isBlank()) {
            return Optional.empty();
        }

        String id = rawWardId.trim().toUpperCase();

        return Optional.ofNullable(wards.get(id));
    }

    /**
     * Returns the number of wards currently held in the catalogue.
     */
    public int size() {
        return wards.size();
    }
}