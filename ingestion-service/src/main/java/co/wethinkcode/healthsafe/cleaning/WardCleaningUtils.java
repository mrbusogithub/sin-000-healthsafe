package co.wethinkcode.healthsafe.cleaning;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Contains pure data-cleaning rules used by WardCsvLoader.
 *
 * This class does not perform file or network operations, making
 * the cleaning rules independently testable.
 */
public final class WardCleaningUtils {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "",
            "n/a",
            "na",
            "tbd",
            "unknown",
            "-",
            "nan",
            "null",
            "none",
            "?"
    );

    private static final Set<String> ACRONYMS = Set.of(
            "ICU",
            "ER",
            "OR",
            "MRI",
            "CT"
    );

    private static final Map<String, String> DEPARTMENT_ALIASES = Map.of(
            "Pediatrics", "Paediatrics",
            "Pediatric", "Paediatrics",
            "Paediatric", "Paediatrics"
    );

    private WardCleaningUtils() {
    }

    /**
     * Removes leading and trailing whitespace and collapses
     * repeated whitespace into a single space.
     */
    public static String trimAndCollapse(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().replaceAll("\\s+", " ");
    }

    /**
     * Normalises department and wing names while preserving
     * recognised medical acronyms.
     */
    public static String toTitleCase(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? null : "";
        }

        String[] words = value.trim().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (result.length() > 0) {
                result.append(' ');
            }

            String upper = word.toUpperCase(Locale.ROOT);

            if (ACRONYMS.contains(upper)) {
                result.append(upper);
            } else if (
                    word.length() >= 2
                            && word.length() <= 5
                            && word.equals(upper)
            ) {
                result.append(upper);
            } else {
                result.append(
                        Character.toUpperCase(word.charAt(0))
                );

                if (word.length() > 1) {
                    result.append(
                            word.substring(1).toLowerCase(Locale.ROOT)
                    );
                }
            }
        }

        return result.toString();
    }

    /**
     * Normalises ward IDs, for example "w-05" becomes "W-05".
     */
    public static String normalizeWardId(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Applies department casing rules and known aliases.
     */
    public static String normalizeDepartment(String value) {
        String normalized = toTitleCase(trimAndCollapse(value));

        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        return DEPARTMENT_ALIASES.getOrDefault(
                normalized,
                normalized
        );
    }

    public static boolean isPlaceholder(String value) {
        if (value == null) {
            return true;
        }

        return PLACEHOLDERS.contains(
                value.trim().toLowerCase(Locale.ROOT)
        );
    }

    /**
     * Holds both the parsed bed count and any explanation
     * of why a value could not be accepted.
     */
    public static final class BedsParseResult {

        public final Integer value;
        public final String note;

        public BedsParseResult(Integer value, String note) {
            this.value = value;
            this.note = note;
        }
    }

    /**
     * Converts the beds_available CSV value into a safe integer.
     *
     * Invalid values are represented as null and accompanied
     * by a note instead of crashing the entire import.
     */
    public static BedsParseResult parseBedCount(String raw) {

        if (raw == null || raw.trim().isEmpty()) {
            return new BedsParseResult(
                    null,
                    "bedsAvailable was blank — flagged"
            );
        }

        String value = raw.trim().toLowerCase(Locale.ROOT);

        if (PLACEHOLDERS.contains(value)) {
            return new BedsParseResult(
                    null,
                    "bedsAvailable was '" + raw.trim()
                            + "' — flagged as missing/placeholder"
            );
        }

        if (value.equals("full")) {
            return new BedsParseResult(
                    0,
                    "bedsAvailable was 'full' — interpreted as 0"
            );
        }

        try {
            int number = Integer.parseInt(value);

            if (number < 0) {
                return new BedsParseResult(
                        null,
                        "bedsAvailable was negative (" + number + ") — flagged"
                );
            }

            if (number > 1000) {
                return new BedsParseResult(
                        null,
                        "bedsAvailable was unrealistic (" + number + ") — flagged"
                );
            }

            return new BedsParseResult(number, null);

        } catch (NumberFormatException exception) {
            return new BedsParseResult(
                    null,
                    "bedsAvailable was non-numeric ('" + raw.trim()
                            + "') — flagged for follow-up"
            );
        }
    }
}