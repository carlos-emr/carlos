package ca.ontario.health.edt;

import java.math.BigInteger;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

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
