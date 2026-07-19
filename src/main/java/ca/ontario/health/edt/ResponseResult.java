package ca.ontario.health.edt;

import javax.xml.bind.annotation.XmlElement;
import java.math.BigInteger;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "responseResult", propOrder = { "description", "resourceID", "result", "status" })
public class ResponseResult
{
    protected String description;
    @XmlElement(required = true)
    protected BigInteger resourceID;
    @XmlElement(required = true)
    protected CommonResult result;
    @XmlElement(required = true)
    protected ResourceStatus status;
    
    public String getDescription() {
        return this.description;
    }
    
    public void setDescription(final String value) {
        this.description = value;
    }
    
    public BigInteger getResourceID() {
        return this.resourceID;
    }
    
    public void setResourceID(final BigInteger value) {
        this.resourceID = value;
    }
    
    public CommonResult getResult() {
        return this.result;
    }
    
    public void setResult(final CommonResult value) {
        this.result = value;
    }
    
    public ResourceStatus getStatus() {
        return this.status;
    }
    
    public void setStatus(final ResourceStatus value) {
        this.status = value;
    }
}
