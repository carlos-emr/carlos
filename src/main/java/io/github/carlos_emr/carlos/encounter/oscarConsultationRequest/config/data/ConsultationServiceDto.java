package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Data transfer object carrying ConsultationServiceDto data across application boundaries without exposing internal domain logic.
 */
public class ConsultationServiceDto {
    private Integer serviceId;
    private String serviceDesc;

    public ConsultationServiceDto(Integer serviceId, String serviceDesc) {
        this.serviceId = serviceId;
        this.serviceDesc = serviceDesc;
    }


    // Getserviceid is exposed here to satisfy the external component interface contract without exposing internal state.
    public Integer getServiceId() { return serviceId; }
    public String getServiceDesc() { return serviceDesc; }
}