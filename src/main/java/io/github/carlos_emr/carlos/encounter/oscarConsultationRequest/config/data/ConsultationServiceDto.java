package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Data Transfer Object carrying ConsultationService data across architectural boundaries.
 *
 * @since 2026-06-10
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