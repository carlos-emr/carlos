package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Configuration bootstrap class for ConsultationServiceDto.
 * Responsible for initializing core beans and setting up environment-specific properties during the Spring context startup phase.
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