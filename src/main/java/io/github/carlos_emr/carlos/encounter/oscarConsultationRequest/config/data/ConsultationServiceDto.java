package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * Data Transfer Object (DTO) for Consultation Service Dto.
 * Encapsulates data transferred between application layers.
 *
 * @since 2026-05-05
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