package co.wethinkcode.healthsafe.cleaning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure helper methods in WardCleaningUtils.
 *
 * Testing the smallest building blocks of ingestion: trimming, casing,
 * ID normalization, department aliasing, and safe bed-count parsing.
 */

public class WardCleaningUtilsTest {
    // Whitespace handling
    @Test
    void trimsAndCollapsesWhitespace() {
        assertEquals("East Wing", WardCleaningUtils.trimAndCollapse("  East   Wing "));
        assertEquals("South Wing", WardCleaningUtils.trimAndCollapse("South  Wing"));
        assertEquals("", WardCleaningUtils.trimAndCollapse(null));
    }

    // Casing rules
    @Test
    void titleCasesNamesButKeepsAcronyms() {
        assertEquals("East Wing", WardCleaningUtils.toTitleCase("east wing"));
        assertEquals("Paediatrics", WardCleaningUtils.toTitleCase("PAEDIATRICS"));
        assertEquals("ICU", WardCleaningUtils.toTitleCase("ICU"));
        assertEquals("Radiology", WardCleaningUtils.toTitleCase("radiology"));
    }

    // Ward ID normalization
    @Test
    void normalizesWardIds() {
        assertEquals("W-05", WardCleaningUtils.normalizeWardId("w-05"));
        assertEquals("W-05", WardCleaningUtils.normalizeWardId(" W-05 "));
        assertNull(WardCleaningUtils.normalizeWardId("   "));
    }

    // Department alias normalization
    @Test
    void normalizesDepartmentAliases() {
        assertEquals("Paediatrics", WardCleaningUtils.normalizeDepartment("pediatrics"));
        assertEquals("Paediatrics", WardCleaningUtils.normalizeDepartment("PAEDIATRICS"));
        assertEquals("ICU", WardCleaningUtils.normalizeDepartment("icu"));
        assertNull(WardCleaningUtils.normalizeDepartment(""));
    }

    // Bed-count parsing — the happy path
    @Test
    void parsesNumericBedCounts() {
        assertEquals(Integer.valueOf(3), WardCleaningUtils.parseBedCount("3").value);
        assertEquals(Integer.valueOf(0), WardCleaningUtils.parseBedCount("0").value);
    }

    // ------------------------------------------------------------------
    // Bed-count parsing — invalid inputs must not crash
    // ------------------------------------------------------------------
    @Test
    void flagsNegativeBedsAsInvalid() {
        WardCleaningUtils.BedsParseResult r = WardCleaningUtils.parseBedCount("-1");
        assertNull(r.value);
        assertNotNull(r.note);
        assertTrue(r.note.toLowerCase().contains("negative"));
    }

    @Test
    void flagsUnrealisticBedsAsInvalid() {
        WardCleaningUtils.BedsParseResult r = WardCleaningUtils.parseBedCount("2023");
        assertNull(r.value);
        assertTrue(r.note.toLowerCase().contains("unrealistic"));
    }

    @Test
    void flagsNonNumericBedsAsInvalid() {
        WardCleaningUtils.BedsParseResult r = WardCleaningUtils.parseBedCount("five");
        assertNull(r.value);
        assertTrue(r.note.contains("non-numeric"));
    }

    @Test
    void flagsPlaceholderBedsAsInvalid() {
        for (String v : new String[]{"N/A", "tbd", "-", "NaN", "", "unknown"}) {
            WardCleaningUtils.BedsParseResult r = WardCleaningUtils.parseBedCount(v);
            assertNull(r.value, "expected null for " + v);
            assertNotNull(r.note, "expected note for " + v);
        }
    }

    @Test
    void interpretsFullAsZero() {
        WardCleaningUtils.BedsParseResult r = WardCleaningUtils.parseBedCount("full");
        assertEquals(Integer.valueOf(0), r.value);
        assertTrue(r.note.toLowerCase().contains("full"));
    }
}
