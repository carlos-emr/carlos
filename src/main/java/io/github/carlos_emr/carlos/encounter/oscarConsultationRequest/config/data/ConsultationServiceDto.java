package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * Data Transfer Object for consultation service details.
 *
 * <p>Transports information regarding the specific type of care or procedure
 * being requested from a specialist.</p>
 */

public class ConsultationServiceDto {
    // Service codes must map directly to the provincial referral catalog
    private Integer serviceId;
    private String serviceDesc;

    public ConsultationServiceDto(Integer serviceId, String serviceDesc) {
        this.serviceId = serviceId;
        this.serviceDesc = serviceDesc;
    }

    public Integer getServiceId() { return serviceId; }
    public String getServiceDesc() { return serviceDesc; }
}