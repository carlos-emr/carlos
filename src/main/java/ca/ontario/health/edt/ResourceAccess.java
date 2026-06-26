package ca.ontario.health.edt;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Represents the ResourceAccess data structure for the Ontario Health EDT integration.
 */
@XmlType(name = "resourceAccess")
@XmlEnum
public enum ResourceAccess
{
    // Maps EDT ResourceAccess fields for external communication

    UPLOAD, 
    DOWNLOAD, 
    BOTH;
    
    public String value() {
        return this.name();
    }
    
    public static ResourceAccess fromValue(final String v) {
        return valueOf(v);
    }
}
