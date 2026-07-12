package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Represents the response payload for the secondary health check web service.
 *
 * <p>Used primarily for system integration testing and endpoint verification.</p>
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld2Response", propOrder = { "_return" })
    // Validate that the response payload adheres to the defined WSDL schema
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
