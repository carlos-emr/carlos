package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * Data Transfer Object for consultation service configuration.
 * <p>
 * Defines the service parameters and associated metadata used during the creation
 * of consultation requests in CARLOS EMR.
 * </p>
 */


public class ConsultationServiceDto {
    private Integer serviceId;
    private String serviceDesc;

    public ConsultationServiceDto(Integer serviceId, String serviceDesc) {
        // Map service configuration attributes to ensure correct routing of the consultation request.
        this.serviceId = serviceId;
        this.serviceDesc = serviceDesc;
    }

    public Integer getServiceId() { return serviceId; }
    public String getServiceDesc() { return serviceDesc; }
}