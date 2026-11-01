package co.wethinkcode.healthsafe.infrastructure.csv;

import co.wethinkcode.healthsafe.domain.WardRecord;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WardCsvLoaderTest {

    private final WardCsvLoader loader = new WardCsvLoader();

    private List<WardRecord> loadSample() throws Exception {
        return loader.loadFromResource("/wards-outdated.csv");
    }

    private WardRecord find(List<WardRecord> records, String wardId) {
        return records.stream()
                .filter(record -> wardId.equals(record.getWardId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void loadsAndDeduplicatesExistingDataset() throws Exception {

        List<WardRecord> records = loadSample();

        assertEquals(17, records.size());
    }

    @Test
    void cleansAndNormalisesWardFields() throws Exception {

        WardRecord ward = find(loadSample(), "W-02");

        assertEquals("West Wing", ward.getWing());
        assertEquals("Paediatrics", ward.getDepartment());
        assertNull(ward.getBedsAvailable());
    }

    @Test
    void keepsBetterDuplicateAndRetainsNotes() throws Exception {

        WardRecord ward = find(loadSample(), "W-05");

        assertEquals(5, ward.getBedsAvailable());

        assertTrue(ward.getNotes().contains("non-numeric"));
        assertTrue(ward.getNotes().contains("duplicate"));
    }

    @Test
    void rejectsCsvWithMissingRequiredHeader() {

        String csv = """
                ward_id,wing,department
                W-01,East Wing,Cardiology
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(new StringReader(csv))
        );
    }

    @Test
    void skipsMalformedRowsAndKeepsValidRows() throws Exception {

        String csv = """
                ward_id,wing,department,beds_available
                W-01,East Wing,Cardiology,4
                W-02,West Wing
                """;

        List<WardRecord> records =
                loader.load(new StringReader(csv));

        assertEquals(1, records.size());
        assertEquals("W-01", records.get(0).getWardId());
    }

    @Test
    void resolvesTiedDuplicateScoresIndependentlyOfRowOrder()
            throws Exception {

        String firstOrder = """
                ward_id,wing,department,beds_available
                W-01,North Wing,Oncology,4
                w-01,East Wing,Cardiology,4
                """;

        String reversedOrder = """
                ward_id,wing,department,beds_available
                w-01,East Wing,Cardiology,4
                W-01,North Wing,Oncology,4
                """;

        List<WardRecord> firstResult =
                loader.load(new StringReader(firstOrder));

        List<WardRecord> secondResult =
                loader.load(new StringReader(reversedOrder));

        assertEquals(1, firstResult.size());
        assertEquals(1, secondResult.size());

        assertEquals(
                firstResult.get(0).getWing(),
                secondResult.get(0).getWing()
        );

        assertEquals(
                firstResult.get(0).getDepartment(),
                secondResult.get(0).getDepartment()
        );

        assertEquals("East Wing", firstResult.get(0).getWing());
    }

    @Test
    void rejectsCompletelyEmptyCsv() {

        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(new StringReader(""))
        );
    }

    @Test
    void rejectsCsvThatContainsHeadersButNoUsableRecords() {

        String csv = """
                ward_id,wing,department,beds_available
                """;

        assertThrows(
                IllegalStateException.class,
                () -> loader.load(new StringReader(csv))
        );
    }
}