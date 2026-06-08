package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Data Transfer Object representing the payload for the HelloWorldResponse web service response.
 * Encapsulates the result data sent back to the SOAP/REST client.
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorldResponse", propOrder = { "_return" })
public class HelloWorldResponse implements Serializable
{
    // Unique identifier for reliable serialization across different JVMs
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
