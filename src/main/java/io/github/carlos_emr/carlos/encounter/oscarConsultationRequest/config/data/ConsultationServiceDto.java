package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;


/**
 * Data Transfer Object defining the specific medical service requested during a consultation referral.
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