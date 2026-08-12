package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;
/**
 * Domain model representing ConsultationServiceDto data structures within the CARLOS EMR system, including state and relationships.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

public class ConsultationServiceDto {
    private Integer serviceId;
    private String serviceDesc;

    public ConsultationServiceDto(Integer serviceId, String serviceDesc) {
        // Internal logic boundary for ConsultationServiceDto state management
        this.serviceId = serviceId;
        this.serviceDesc = serviceDesc;
    }

    public Integer getServiceId() { return serviceId; }
    public String getServiceDesc() { return serviceDesc; }
}