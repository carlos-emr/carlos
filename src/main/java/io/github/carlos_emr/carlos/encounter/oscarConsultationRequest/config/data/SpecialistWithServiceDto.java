package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DTO representing a specialist with all services they belong to.
 * Used by the consultation form autocomplete to allow searching across all specialties.
 *
 * @since 2026-03-29
 */
public class SpecialistWithServiceDto {
    private Integer specId;
    private String name;
    private String phone;
    private String fax;
    private String address;
    private String annotation;
    private List<Integer> serviceIds;
    private List<String> serviceNames;

    public SpecialistWithServiceDto(Integer specId, String name, String phone,
                                    String fax, String address, String annotation,
                                    List<Integer> serviceIds, List<String> serviceNames) {
        this.specId = specId;
        this.name = name;
        this.phone = phone;
        this.fax = fax;
        this.address = address;
        this.annotation = annotation;
        this.serviceIds = serviceIds == null ? null : new ArrayList<>(serviceIds);
        this.serviceNames = serviceNames == null ? null : new ArrayList<>(serviceNames);
    }

    public Integer getSpecId() { return specId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getFax() { return fax; }
    public String getAddress() { return address; }
    public String getAnnotation() { return annotation; }
    public List<Integer> getServiceIds() { return serviceIds == null ? null : Collections.unmodifiableList(serviceIds); }
    public List<String> getServiceNames() { return serviceNames == null ? null : Collections.unmodifiableList(serviceNames); }

    /**
     * Appends a service association to this specialist's service lists.
     * Used when grouping specialists across multiple services.
     *
     * @param serviceId   the service ID to add
     * @param serviceName the service name to add
     */
    public void addService(Integer serviceId, String serviceName) {
        if (this.serviceIds == null) this.serviceIds = new ArrayList<>();
        if (this.serviceNames == null) this.serviceNames = new ArrayList<>();
        this.serviceIds.add(serviceId);
        this.serviceNames.add(serviceName);
    }
}
