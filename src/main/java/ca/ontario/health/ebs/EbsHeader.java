package ca.ontario.health.ebs;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ebs_header", propOrder = { "softwareConformanceKey", "auditId" })
public class EbsHeader
{
    @XmlElement(name = "SoftwareConformanceKey", required = true)
    protected String softwareConformanceKey;
    @XmlElement(name = "AuditId", required = true)
    protected String auditId;
    
    public String getSoftwareConformanceKey() {
        return this.softwareConformanceKey;
    }
    
    public void setSoftwareConformanceKey(final String value) {
        this.softwareConformanceKey = value;
    }
    
    public String getAuditId() {
        return this.auditId;
    }
    
    public void setAuditId(final String value) {
        this.auditId = value;
    }
}
