package co.wethinkcode.healthsafe;

/**
 * Clean, normalized ward record. This is the JSON shape exposed by GET /wards
 * and consumed by ward-service.
 */

public class WardRecord {
    private String wardId;
    private String wing;
    private String department;
    private Integer bedsAvailable;
    private String notes;

    public WardRecord() {
    }

    public WardRecord(String wardId, String wing, String department, Integer bedsAvailable, String notes) {
        this.wardId = wardId;
        this.wing = wing;
        this.department = department;
        this.bedsAvailable = bedsAvailable;
        this.notes = notes;
    }

    public WardRecord copy() {
        return new WardRecord(wardId, wing, department, bedsAvailable, notes);
    }

    public String getWardId() { return wardId; }
    public void setWardId(String wardId) { this.wardId = wardId; }

    public String getWing() { return wing; }
    public void setWing(String wing) { this.wing = wing; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Integer getBedsAvailable() { return bedsAvailable; }
    public void setBedsAvailable(Integer bedsAvailable) { this.bedsAvailable = bedsAvailable; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
