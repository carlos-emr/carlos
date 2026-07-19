package ca.ontario.health.edt;

import java.math.BigInteger;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "list", propOrder = { "resourceType", "status", "pageNo" })
public class List
{
    protected String resourceType;
    protected ResourceStatus status;
    protected BigInteger pageNo;
    
    public String getResourceType() {
        return this.resourceType;
    }
    
    public void setResourceType(final String value) {
        this.resourceType = value;
    }
    
    public ResourceStatus getStatus() {
        return this.status;
    }
    
    public void setStatus(final ResourceStatus value) {
        this.status = value;
    }
    
    public BigInteger getPageNo() {
        return this.pageNo;
    }
    
    public void setPageNo(final BigInteger value) {
        this.pageNo = value;
    }
}
