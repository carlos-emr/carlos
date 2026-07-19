package ca.ontario.health.edt;

import java.math.BigInteger;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "downloadData", propOrder = { "content", "resourceID", "resourceType", "description", "result" })
public class DownloadData
{
    @XmlElement(required = true)
    protected byte[] content;
    @XmlElement(required = true)
    protected BigInteger resourceID;
    @XmlElement(required = true)
    protected String resourceType;
    @XmlElement(required = true)
    protected String description;
    @XmlElement(required = true)
    protected CommonResult result;
    
    public byte[] getContent() {
        return this.content;
    }
    
    public void setContent(final byte[] value) {
        this.content = value;
    }
    
    public BigInteger getResourceID() {
        return this.resourceID;
    }
    
    public void setResourceID(final BigInteger value) {
        this.resourceID = value;
    }
    
    public String getResourceType() {
        return this.resourceType;
    }
    
    public void setResourceType(final String value) {
        this.resourceType = value;
    }
    
    public String getDescription() {
        return this.description;
    }
    
    public void setDescription(final String value) {
        this.description = value;
    }
    
    public CommonResult getResult() {
        return this.result;
    }
    
    public void setResult(final CommonResult value) {
        this.result = value;
    }
}
