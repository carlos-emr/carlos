package ca.ontario.health.edt;

import java.math.BigInteger;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "detailData", propOrder = { "createTimestamp", "description", "resourceType", "modifyTimestamp", "resourceID", "result", "status" })
public class DetailData
{
    @XmlElement(required = true)
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar createTimestamp;
    protected String description;
    protected String resourceType;
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar modifyTimestamp;
    @XmlElement(required = true)
    protected BigInteger resourceID;
    @XmlElement(required = true)
    protected CommonResult result;
    @XmlElement(required = true)
    protected ResourceStatus status;
    
    public XMLGregorianCalendar getCreateTimestamp() {
        return this.createTimestamp;
    }
    
    public void setCreateTimestamp(final XMLGregorianCalendar value) {
        this.createTimestamp = value;
    }
    
    public String getDescription() {
        return this.description;
    }
    
    public void setDescription(final String value) {
        this.description = value;
    }
    
    public String getResourceType() {
        return this.resourceType;
    }
    
    public void setResourceType(final String value) {
        this.resourceType = value;
    }
    
    public XMLGregorianCalendar getModifyTimestamp() {
        return this.modifyTimestamp;
    }
    
    public void setModifyTimestamp(final XMLGregorianCalendar value) {
        this.modifyTimestamp = value;
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
