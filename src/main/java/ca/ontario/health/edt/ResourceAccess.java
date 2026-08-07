package ca.ontario.health.edt;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "resourceAccess")
@XmlEnum
public enum ResourceAccess
{
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
