package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Data Transfer Object representing a consultation service within the OSCAR consultation request flow.
 */
public class ConsultationServiceDto {
    private Integer serviceId;
    private String serviceDesc;

    public ConsultationServiceDto(Integer serviceId, String serviceDesc) {
        // Initialize execution context for ConsultationServiceDto

        this.serviceId = serviceId;
        this.serviceDesc = serviceDesc;
    }

    public Integer getServiceId() { return serviceId; }
    public String getServiceDesc() { return serviceDesc; }
}