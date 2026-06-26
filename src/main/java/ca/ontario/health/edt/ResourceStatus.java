package ca.ontario.health.edt;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Represents the ResourceStatus data structure for the Ontario Health EDT integration.
 */
@XmlType(name = "resourceStatus")
@XmlEnum
public enum ResourceStatus
{
    // Maps EDT ResourceStatus fields for external communication

    UPLOADED, 
    SUBMITTED, 
    WIP, 
    DOWNLOADABLE, 
    DELETED, 
    APPROVED, 
    DENIED;
    
    public String value() {
        return this.name();
    }
    
    public static ResourceStatus fromValue(final String v) {
        return valueOf(v);
    }
}
