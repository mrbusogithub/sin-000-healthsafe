package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests prove that all the small rules work together when fed the
 * messy legacy file that this service exists to handle.
 */

public class WardCsvLoaderTest {

     // load the real CSV once per test.
    private List<WardRecord> loadSample() throws Exception {
        return new WardCsvLoader().loadFromResource("/wards-outdated.csv");
    }

    // Locate a single ward by ID, or fail the test if it isn't present.
    private WardRecord find(List<WardRecord> list, String id) {
        return list.stream()
                .filter(r -> id.equals(r.getWardId()))
                .findFirst()
                .orElseThrow();
    }

    // Overall parse health
    @Test
    void parsesAllRowsWithoutCrashing() throws Exception {
        List<WardRecord> result = loadSample();
        assertNotNull(result);
        assertEquals(16, result.size());
    }

    // Deduplication
    @Test
    void deduplicatesW05CaseInsensitively() throws Exception {
        WardRecord w05 = find(loadSample(), "W-05");
        assertEquals(Integer.valueOf(5), w05.getBedsAvailable());
        assertTrue(w05.getNotes() != null && w05.getNotes().contains("non-numeric"));
        assertTrue(w05.getNotes() != null && w05.getNotes().contains("duplicate"));
    }

    // Casing and padding
    @Test
    void normalisesCasingAndWingPadding() throws Exception {
        WardRecord w02 = find(loadSample(), "W-02");
        assertEquals("West Wing", w02.getWing());
        assertEquals("Paediatrics", w02.getDepartment());
        assertNull(w02.getBedsAvailable());
    }

    @Test
    void collapsesDoubleSpacesInWingNames() throws Exception {
        WardRecord w10 = find(loadSample(), "W-10");
        assertEquals("South Wing", w10.getWing());
    }
}
