package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * WS response representation for the simple connectivity check endpoint.
 * Carries basic string responses back to the client.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorldResponse", propOrder = { "_return" })
public class HelloWorldResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected String _return;
    
    public String getReturn() {
        // Basic payload wrapper carrying success/failure states for simple WS checks
        return this._return;
    }
    
    public void setReturn(final String return1) {
        this._return = return1;
    }
}
