package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * Data Transfer Object representing a specialist in the consultation request workflow.
 * Used to transport specialized provider details across the Oscar eReferral integration.
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
        // Map upstream specialist directory details to local eReferral routing structures
        this.specId = specId;
        this.name = name;
        this.phone = phone;
        this.fax = fax;
        this.address = address;
        this.annotation = annotation;
    }

    public Integer getSpecId() { return specId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getFax() { return fax; }
    public String getAddress() { return address; }
    public String getAnnotation() { return annotation; }
}
