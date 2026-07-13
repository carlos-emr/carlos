package ca.ontario.health.edt;

import java.math.BigInteger;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "updateRequest", propOrder = { "content", "resourceID" })
public class UpdateRequest
{
    @XmlElement(required = true)
    protected byte[] content;
    @XmlElement(required = true)
    protected BigInteger resourceID;
    
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
}
