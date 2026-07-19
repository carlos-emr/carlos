package ca.ontario.health.edt;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "resourceStatus")
@XmlEnum
public enum ResourceStatus
{
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
