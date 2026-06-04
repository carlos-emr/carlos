package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * JAXB wrapper for the response payload of the initial HelloWorld service endpoint.
 * Confirms successful reception of the diagnostic ping and validates the return communication channel.
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorldResponse", propOrder = { "_return" })
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
