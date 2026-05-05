package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * ConsultationServiceDto provides functionality and data models for the ConsultationServiceDto domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
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