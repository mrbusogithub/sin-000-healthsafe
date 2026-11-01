package co.wethinkcode.healthsafe.infrastructure.csv;

import co.wethinkcode.healthsafe.cleaning.WardCleaningUtils;
import co.wethinkcode.healthsafe.domain.WardRecord;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Loads, cleans and deduplicates the legacy ward CSV.
 *
 * Expected headers:
 * ward_id, Wing, department, beds_available
 */
public class WardCsvLoader {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "ward_id",
            "wing",
            "department",
            "beds_available"
    );

    /**
     * Loads a CSV file from the application's classpath resources.
     */
    public List<WardRecord> loadFromResource(String resourcePath)
            throws IOException {

        InputStream stream =
                WardCsvLoader.class.getResourceAsStream(resourcePath);

        if (stream == null) {
            throw new IllegalStateException(
                    "Missing required CSV resource: " + resourcePath
            );
        }

        try (
                InputStream input = stream;
                Reader reader = new InputStreamReader(
                        input,
                        StandardCharsets.UTF_8
                )
        ) {
            return load(reader);
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads a CSV from a Reader.
     *
     * Keeping this method separate makes it possible to test
     * CSV behaviour using StringReader without filesystem access.
     */
    public List<WardRecord> load(Reader reader) throws IOException, CsvValidationException {

        List<WardRecord> records = new ArrayList<>();

        try (CSVReader csv = new CSVReader(reader)) {

            String[] headers = csv.readNext();

            if (headers == null) {
                throw new IllegalArgumentException(
                        "CSV is empty: expected a header row"
                );
            }

            Map<String, Integer> indexes = indexHeaders(headers);

            validateRequiredHeaders(indexes);

            int wardIdIndex = indexes.get("ward_id");
            int wingIndex = indexes.get("wing");
            int departmentIndex = indexes.get("department");
            int bedsIndex = indexes.get("beds_available");

            int recordNumber = 1;
            String[] row;

            while ((row = csv.readNext()) != null) {
                recordNumber++;

                try {
                    WardRecord record = parseRow(
                            row,
                            wardIdIndex,
                            wingIndex,
                            departmentIndex,
                            bedsIndex
                    );

                    if (record != null) {
                        records.add(record);
                    }

                } catch (IllegalArgumentException exception) {
                    System.err.printf(
                            "[ingestion] skipping malformed CSV record %d: %s%n",
                            recordNumber,
                            exception.getMessage()
                    );
                }
            }
        }

        if (records.isEmpty()) {
            throw new IllegalStateException(
                    "CSV contains no usable ward records"
            );
        }

        return deduplicate(records);
    }

    /**
     * Builds a lookup of header names to their actual CSV positions.
     *
     * The loader deliberately does not use fixed column positions
     * when a required header is absent.
     */
    private Map<String, Integer> indexHeaders(String[] headers) {

        Map<String, Integer> indexes = new HashMap<>();

        for (int i = 0; i < headers.length; i++) {

            String name = headers[i]
                    .trim()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", "_");

            if (name.isBlank()) {
                throw new IllegalArgumentException(
                        "CSV contains a blank header"
                );
            }

            if (indexes.putIfAbsent(name, i) != null) {
                throw new IllegalArgumentException(
                        "Duplicate CSV header: " + name
                );
            }
        }

        return indexes;
    }

    /**
     * Ensures the CSV has every column required by the application.
     */
    private void validateRequiredHeaders(Map<String, Integer> indexes) {

        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> !indexes.containsKey(header))
                .toList();

        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV is missing required headers: "
                            + String.join(", ", missingHeaders)
            );
        }
    }

    /**
     * Converts one CSV row into a cleaned domain object.
     */
    private WardRecord parseRow(
            String[] row,
            int wardIdIndex,
            int wingIndex,
            int departmentIndex,
            int bedsIndex
    ) {

        int highestRequiredIndex = Math.max(
                Math.max(wardIdIndex, wingIndex),
                Math.max(departmentIndex, bedsIndex)
        );

        if (row.length <= highestRequiredIndex) {
            throw new IllegalArgumentException(
                    "row contains fewer columns than the header"
            );
        }

        String wardId = WardCleaningUtils.normalizeWardId(
                row[wardIdIndex]
        );

        if (wardId == null) {
            throw new IllegalArgumentException(
                    "ward_id is missing or blank"
            );
        }

        String wing = WardCleaningUtils.toTitleCase(
                WardCleaningUtils.trimAndCollapse(row[wingIndex])
        );

        if (wing == null || wing.isBlank()) {
            wing = null;
        }

        String department = WardCleaningUtils.normalizeDepartment(
                row[departmentIndex]
        );

        WardCleaningUtils.BedsParseResult beds =
                WardCleaningUtils.parseBedCount(row[bedsIndex]);

        return new WardRecord(
                wardId,
                wing,
                department,
                beds.value,
                beds.note
        );
    }

    /**
     * Groups duplicate IDs and merges each group.
     */
    private List<WardRecord> deduplicate(List<WardRecord> records) {

        Map<String, List<WardRecord>> grouped = new HashMap<>();

        for (WardRecord record : records) {
            grouped.computeIfAbsent(
                    record.getWardId(),
                    ignored -> new ArrayList<>()
            ).add(record);
        }

        List<WardRecord> result = new ArrayList<>();

        for (List<WardRecord> group : grouped.values()) {
            result.add(mergeGroup(group));
        }

        result.sort(
                Comparator.comparing(WardRecord::getWardId)
        );

        return result;
    }

    /**
     * Selects the strongest record and retains all distinct notes.
     */
    private WardRecord mergeGroup(List<WardRecord> group) {

        // The complete ordering makes tied scores independent
        // of the order in which CSV rows were originally supplied.
        WardRecord best = group.stream()
                .min(candidateOrdering())
                .orElseThrow();

        TreeSet<String> notes = new TreeSet<>();

        for (WardRecord record : group) {
            if (record.getNotes() != null
                    && !record.getNotes().isBlank()) {
                notes.add(record.getNotes());
            }
        }

        if (group.size() > 1) {
            notes.add(
                    "merged " + group.size()
                            + " duplicate records deterministically"
            );
        }

        WardRecord merged = best.copy();

        if (!notes.isEmpty()) {
            merged.setNotes(String.join("; ", notes));
        }

        return merged;
    }

    /**
     * Higher data-quality scores win.
     *
     * If scores are equal, normalised field values provide a stable
     * secondary ordering. Null values sort after populated values.
     */
    private Comparator<WardRecord> candidateOrdering() {

        return Comparator.comparingInt(this::score)
                .reversed()
                .thenComparing(record -> sortValue(record.getWing()))
                .thenComparing(record -> sortValue(record.getDepartment()))
                .thenComparingInt(record ->
                        record.getBedsAvailable() == null
                                ? Integer.MAX_VALUE
                                : record.getBedsAvailable()
                )
                .thenComparing(record -> sortValue(record.getNotes()));
    }

    private String sortValue(String value) {
        return value == null ? "\uFFFF" : value;
    }

    /**
     * Existing data-quality scoring strategy:
     * valid bed count = 10 points
     * populated wing = 1 point
     * populated department = 1 point
     */
    private int score(WardRecord record) {

        int score = 0;

        if (record.getBedsAvailable() != null) {
            score += 10;
        }

        if (record.getWing() != null
                && !record.getWing().isBlank()) {
            score += 1;
        }

        if (record.getDepartment() != null
                && !record.getDepartment().isBlank()) {
            score += 1;
        }

        return score;
    }
}