package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Response wrapper for the HelloWorld2 WS testing endpoint.
 * Used primarily to verify endpoint availability and marshalling.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld2Response", propOrder = { "_return" })
public class HelloWorld2Response implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected String _return;
    
    public String getReturn() {
        // Serialize simple response structures primarily for endpoint health verification
        return this._return;
    }
    
    public void setReturn(final String return1) {
        this._return = return1;
    }
}
