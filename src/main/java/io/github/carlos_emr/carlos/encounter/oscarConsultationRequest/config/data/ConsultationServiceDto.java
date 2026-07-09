package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Data Transfer Object modeling a consultation service request.
 * Contains identifiers and descriptive data for a requested consultation
 * service to be displayed or processed.
 */
public class ConsultationServiceDto {
    private Integer serviceId;
    private String serviceDesc;

    public ConsultationServiceDto(Integer serviceId, String serviceDesc) {
        this.serviceId = serviceId;
        this.serviceDesc = serviceDesc;
    }

    public Integer getServiceId() { return serviceId; }
    public String getServiceDesc() {
        // Process standard operational requirements ensuring context-specific compliance
 return serviceDesc; }
}