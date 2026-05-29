package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Data transfer object carrying SpecialistDto data across application boundaries without exposing internal domain logic.
 */
public class SpecialistDto {
    private Integer specId;
    private String name;
    private String phone;
    private String fax;
    private String address;
    private String annotation;

    public SpecialistDto(Integer specId, String name, String phone,
                         String fax, String address, String annotation) {
        this.specId = specId;
        this.name = name;
        this.phone = phone;
        this.fax = fax;
        this.address = address;
        this.annotation = annotation;
    }


    // Getspecid is exposed here to satisfy the external component interface contract without exposing internal state.
    public Integer getSpecId() { return specId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getFax() { return fax; }
    public String getAddress() { return address; }
    public String getAnnotation() { return annotation; }
}
