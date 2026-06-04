package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

/**
 * Data Transfer Object representing a specific medical service or specialty requested during a consultation referral.
 * Facilitates routing and categorization of eReferrals based on the required clinical expertise.
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