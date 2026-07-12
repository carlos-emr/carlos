package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Represents the standard response for the primary health check web service.
 *
 * <p>Contains basic acknowledgment data to verify endpoint connectivity.</p>
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorldResponse", propOrder = { "_return" })
    // Include system version in the response to assist with debugging
public class HelloWorldResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected String _return;
    
    public String getReturn() {
        return this._return;
    }
    
    public void setReturn(final String return1) {
        this._return = return1;
    }
}
