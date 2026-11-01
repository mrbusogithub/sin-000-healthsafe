package co.wethinkcode.healthsafe.cleaning;

import java.util.Map;
import java.util.Set;

/**
 * Pure helpers used by the CSV loader. Deliberately free of I/O so they can be
 */
public final class WardCleaningUtils {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "", "n/a", "na", "tbd", "unknown", "-", "nan", "null", "none", "?"
    );

    private static final Set<String> ACRONYMS = Set.of("ICU", "ER", "OR", "MRI", "CT");

    /** Known regional-spelling / synonym variants for departments. */
    private static final Map<String, String> DEPARTMENT_ALIASES = Map.of(
            "Pediatrics", "Paediatrics",
            "Pediatric",  "Paediatrics",
            "Paediatric", "Paediatrics"
    );

    private WardCleaningUtils() {
    }

    /** Trim leading/trailing whitespace and collapse any run of whitespace to one space. */
    public static String trimAndCollapse(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }

    /**
     * Title-case each word. Short all-caps tokens (2-5 chars, e.g. ICU) are
     * preserved as acronyms so we don't corrupt domain terms.
     */
    public static String toTitleCase(String s) {
        if (s == null || s.isBlank()) return s == null ? null : "";
        String[] words = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            String w = words[i];
            if (w.isEmpty()) continue;

            String upper = w.toUpperCase();
            if (ACRONYMS.contains(upper)) {
                sb.append(upper);                            // known acronym → force uppercase
            } else if (w.length() >= 2 && w.length() <= 5 && w.equals(upper)) {
                sb.append(upper);                            // looks like an acronym (all-caps input)
            } else {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    /** Uppercase + trim a ward ID ("w-05" / "W-05 " → "W-05"). */
    public static String normalizeWardId(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase();
        return t.isEmpty() ? null : t;
    }

    /** Title-case, collapse-whitespace, then map known aliases. */
    public static String normalizeDepartment(String s) {
        String t = toTitleCase(trimAndCollapse(s));
        if (t == null || t.isBlank()) return null;
        return DEPARTMENT_ALIASES.getOrDefault(t, t);
    }

    public static boolean isPlaceholder(String s) {
        if (s == null) return true;
        return PLACEHOLDERS.contains(s.trim().toLowerCase());
    }

    /** Result of parsing beds_available; keeps the raw diagnosis alongside the value. */
    public static final class BedsParseResult {
        public final Integer value;
        public final String note;

        public BedsParseResult(Integer value, String note) {
            this.value = value;
            this.note = note;
        }
    }

    /**
     * Parse beds_available safely. Rules:
     *   - blank / placeholder       → null, with a descriptive note
     *   - "full"                    → 0   (interpreted: no beds free)
     *   - negative                  → null, flagged
     *   - > 1000 (unrealistic)      → null, flagged
     *   - non-numeric (e.g. "five") → null, flagged
     *   - numeric                   → that number
     */
    public static BedsParseResult parseBedCount(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new BedsParseResult(null, "bedsAvailable was blank — flagged");
        }
        String s = raw.trim().toLowerCase();
        if (PLACEHOLDERS.contains(s)) {
            return new BedsParseResult(null,
                    "bedsAvailable was '" + raw.trim() + "' — flagged as missing/placeholder");
        }
        if (s.equals("full")) {
            return new BedsParseResult(0, "bedsAvailable was 'full' — interpreted as 0");
        }
        try {
            int n = Integer.parseInt(s);
            if (n < 0) {
                return new BedsParseResult(null,
                        "bedsAvailable was negative (" + n + ") — flagged");
            }
            if (n > 1000) {
                return new BedsParseResult(null,
                        "bedsAvailable was unrealistic (" + n + ") — flagged");
            }
            return new BedsParseResult(n, null);
        } catch (NumberFormatException e) {
            return new BedsParseResult(null,
                    "bedsAvailable was non-numeric ('" + raw.trim() + "') — flagged for follow-up");
        }
    }
}