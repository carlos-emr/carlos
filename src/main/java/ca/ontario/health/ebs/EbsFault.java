package ca.ontario.health.ebs;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ebsFault", propOrder = { "code", "message" })
public class EbsFault
{
    @XmlElement(required = true)
    protected String code;
    @XmlElement(required = true)
    protected String message;
    
    public String getCode() {
        return this.code;
    }
    
    public void setCode(final String value) {
        this.code = value;
    }
    
    public String getMessage() {
        return this.message;
    }
    
    public void setMessage(final String value) {
        this.message = value;
    }
}
