package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * Data transfer object capturing service details related to specialist consultations.
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