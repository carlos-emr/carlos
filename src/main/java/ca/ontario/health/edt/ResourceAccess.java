package ca.ontario.health.edt;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

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
