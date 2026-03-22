package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * JAXB response DTO for the HelloWorld2 web service test operation.
 *
 * @since 2012-08-13
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld2Response", propOrder = { "_return" })
public class HelloWorld2Response implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected String _return;
    
    /**
     * Returns the response string from the HelloWorld2 operation.
     *
     * @return String the response value
     */
    public String getReturn() {
        return this._return;
    }
    
    /**
     * Sets the response string for the HelloWorld2 operation.
     *
     * @param return1 String the response value to set
     */
    public void setReturn(final String return1) {
        this._return = return1;
    }
}
