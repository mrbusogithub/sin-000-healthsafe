package co.wethinkcode.healthsafe;

/** Mirrors the JSON shape returned by ingestion-service GET /wards. */
public class Ward {
    private String wardId;
    private String wing;
    private String department;
    private Integer bedsAvailable;
    private String notes;

    public Ward() {
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