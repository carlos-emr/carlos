package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * JAXB-annotated representation of the HelloWorld2Response payload.
 * Used in SOAP/REST web service endpoints for connectivity testing and validation.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld2Response", propOrder = { "_return" })
public class HelloWorld2Response implements Serializable
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
