package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Data transfer object representing a specific medical service offered by a specialist or clinic, used to populate referral option lists.
 */
public class ConsultationServiceDto {
    private Integer serviceId;
    private String serviceDesc;

    public ConsultationServiceDto(Integer serviceId, String serviceDesc) {
        this.serviceId = serviceId;
        this.serviceDesc = serviceDesc;
    }

    public Integer getServiceId() { return serviceId; }
    public String getServiceDesc() { return serviceDesc; }
}