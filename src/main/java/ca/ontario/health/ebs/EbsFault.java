package ca.ontario.health.ebs;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

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
