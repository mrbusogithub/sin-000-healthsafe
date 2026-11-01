package co.wethinkcode.healthsafe.infrastructure.csv;

import co.wethinkcode.healthsafe.cleaning.WardCleaningUtils;
import co.wethinkcode.healthsafe.domain.WardRecord;
import com.opencsv.CSVReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and cleans wards-outdated.csv, then deterministically deduplicates.
 *
 * Merge strategy (documented so it's easy to change later):
 *   1. Group rows by normalized ward_id.
 *   2. Within a group, pick the "best" record — most valid fields wins
 *      (bedsAvailable present = +10, wing present = +1, department present = +1).
 *   3. Concatenate every distinct note from the group so nothing is lost.
 *
 * This makes W-05 (beds=5) beat w-05 (beds="five"), and the merged record
 * still carries the flag about the discarded bad value.
 */
public class WardCsvLoader {

    public List<WardRecord> loadFromResource(String resourcePath) throws Exception {
        try (InputStream in = WardCsvLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource: " + resourcePath);
            }
            return load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    public List<WardRecord> load(Reader reader) throws Exception {
        List<WardRecord> raw = new ArrayList<>();
        try (CSVReader csv = new CSVReader(reader)) {
            String[] header = csv.readNext();
            if (header == null) return Collections.emptyList();

            Map<String, Integer> idx = indexHeader(header);
            int wardIdIdx = idx.getOrDefault("ward_id", 0);
            int wingIdx   = idx.getOrDefault("wing", 1);
            int deptIdx   = idx.getOrDefault("department", 2);
            int bedsIdx   = idx.getOrDefault("beds_available", 3);

            String[] row;
            while ((row = csv.readNext()) != null) {
                try {
                    WardRecord r = parseRow(row, wardIdIdx, wingIdx, deptIdx, bedsIdx);
                    if (r != null) raw.add(r);
                } catch (RuntimeException e) {
                    System.err.println("[ingestion] skipping malformed row: " + e.getMessage());
                }
            }
        }
        return dedupe(raw);
    }

    private WardRecord parseRow(String[] row, int wardIdIdx, int wingIdx, int deptIdx, int bedsIdx) {
        String rawId = get(row, wardIdIdx);
        String wardId = WardCleaningUtils.normalizeWardId(rawId);
        if (wardId == null) return null; // unusable row

        String wing = WardCleaningUtils.toTitleCase(WardCleaningUtils.trimAndCollapse(get(row, wingIdx)));
        if (wing == null || wing.isBlank()) wing = null;

        String department = WardCleaningUtils.normalizeDepartment(get(row, deptIdx));

        WardCleaningUtils.BedsParseResult beds =
                WardCleaningUtils.parseBedCount(get(row, bedsIdx));

        return new WardRecord(wardId, wing, department, beds.value, beds.note);
    }

    private Map<String, Integer> indexHeader(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String k = header[i].trim().toLowerCase().replace(' ', '_');
            map.put(k, i);
        }
        return map;
    }

    private String get(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return null;
        return row[idx];
    }

    // ---- deduplication ----------------------------------------------------

    List<WardRecord> dedupe(List<WardRecord> records) {
        Map<String, List<WardRecord>> grouped = new LinkedHashMap<>();
        for (WardRecord r : records) {
            grouped.computeIfAbsent(r.getWardId(), k -> new ArrayList<>()).add(r);
        }
        List<WardRecord> result = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<WardRecord>> e : grouped.entrySet()) {
            result.add(mergeGroup(e.getValue()));
        }
        result.sort(Comparator.comparing(WardRecord::getWardId));
        return result;
    }

    private WardRecord mergeGroup(List<WardRecord> group) {
        WardRecord best = group.get(0);
        for (WardRecord candidate : group) {
            if (score(candidate) > score(best)) best = candidate;
        }

        Set<String> notes = new LinkedHashSet<>();
        for (WardRecord r : group) {
            if (r.getNotes() != null && !r.getNotes().isBlank()) notes.add(r.getNotes());
        }
        if (group.size() > 1) {
            notes.add("merged " + group.size() + " duplicate records deterministically");
        }

        WardRecord merged = best.copy();
        if (!notes.isEmpty()) merged.setNotes(String.join("; ", notes));
        return merged;
    }

    private int score(WardRecord r) {
        int s = 0;
        if (r.getBedsAvailable() != null) s += 10;
        if (r.getWing() != null && !r.getWing().isBlank()) s += 1;
        if (r.getDepartment() != null && !r.getDepartment().isBlank()) s += 1;
        return s;
    }
}