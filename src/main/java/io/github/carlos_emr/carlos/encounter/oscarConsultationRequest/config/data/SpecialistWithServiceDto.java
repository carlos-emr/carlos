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

    /**
     * Returns the specification ID.
     */
    public Integer getSpecId() { return specId; }
    public String getName() { return name; }
    /**
     * Returns the phone number.
     */
    public String getPhone() { return phone; }
    /**
     * Returns the fax number.
     */
    public String getFax() { return fax; }
    public String getAddress() { return address; }
    /**
     * Returns the annotation string.
     */
    public String getAnnotation() { return annotation; }
    /**
     * Returns an unmodifiable list of service IDs or null if not set.
     */
    public List<Integer> getServiceIds() { return serviceIds == null ? null : Collections.unmodifiableList(serviceIds); }
    /**
     * Returns an unmodifiable list of service names or null if not set.
     */
    public List<String> getServiceNames() { return serviceNames == null ? null : Collections.unmodifiableList(serviceNames); }
}
