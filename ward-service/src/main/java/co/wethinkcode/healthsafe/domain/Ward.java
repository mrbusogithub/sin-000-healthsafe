package co.wethinkcode.healthsafe.domain;

/**
 * Represents a ward in the Ward Service domain.
 *
 * This model mirrors the fields Ward Service needs from the cleaned
 * records supplied by Ingestion Service.
 *
 * It belongs in the domain package because it represents a hospital
 * concept, not an HTTP route or messaging implementation.
 */
public class Ward {

    private String wardId;
    private String wing;
    private String department;
    private Integer bedsAvailable;
    private String notes;

    /*
     * Jackson uses the no-argument constructor when converting the JSON
     * returned by Ingestion Service into Ward objects.
     */
    public Ward() {
    }

    // Standard getters and setters allow Jackson to map JSON properties.

    public String getWardId() {
        return wardId;
    }

    public void setWardId(String wardId) {
        this.wardId = wardId;
    }

    public String getWing() {
        return wing;
    }

    public void setWing(String wing) {
        this.wing = wing;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getBedsAvailable() {
        return bedsAvailable;
    }

    public void setBedsAvailable(Integer bedsAvailable) {
        this.bedsAvailable = bedsAvailable;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}