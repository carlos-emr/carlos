package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * Data Transfer Object representing an available consultation service.
 * Defines the referral pathways available for mapping in the consultation module.
 */

public class ConsultationServiceDto {
    private Integer serviceId;
    private String serviceDesc;

    public ConsultationServiceDto(Integer serviceId, String serviceDesc) {
        // Determine available referral channels based on loaded external service definitions
        this.serviceId = serviceId;
        this.serviceDesc = serviceDesc;
    }

    public Integer getServiceId() { return serviceId; }
    public String getServiceDesc() { return serviceDesc; }
}