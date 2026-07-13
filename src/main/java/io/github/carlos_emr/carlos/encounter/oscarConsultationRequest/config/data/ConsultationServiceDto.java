package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * Data transfer object encapsulating consultation service configurations, mapping service IDs and descriptions for routing.
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