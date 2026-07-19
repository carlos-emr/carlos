package ca.ontario.health.edt;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "uploadData", propOrder = { "content", "description", "resourceType" })
public class UploadData
{
    @XmlElement(required = true)
    protected byte[] content;
    protected String description;
    @XmlElement(required = true)
    protected String resourceType;
    
    public byte[] getContent() {
        return this.content;
    }
    
    public void setContent(final byte[] value) {
        this.content = value;
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
}
