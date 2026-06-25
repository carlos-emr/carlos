package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Data Transfer Object (DTO) for ConsultationService data.
 * Facilitates the decoupled transport of data between the presentation or API layers and the business logic.
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