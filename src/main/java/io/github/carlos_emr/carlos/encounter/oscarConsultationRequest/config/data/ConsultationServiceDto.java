package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * Domain model representing ConsultationServiceDto, used to transfer state across application layers.
 *
 * @since 2026-06-26
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