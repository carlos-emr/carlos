package ca.ontario.health.hcv;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "validate", propOrder = { "requests", "locale" })
public class Validate
{
    @XmlElement(required = true)
    protected Requests requests;
    protected String locale;
    
    public Requests getRequests() {
        return this.requests;
    }
    
    public void setRequests(final Requests value) {
        this.requests = value;
    }
    
    public String getLocale() {
        return this.locale;
    }
    
    public void setLocale(final String value) {
        this.locale = value;
    }
}
