package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Data Transfer Object (DTO) for consultation services.
 * Carries information about specific services requested or provided during
 * a medical consultation referral.
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