package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * JAXB request DTO for the parameterized HelloWorld2 web service test operation.
 *
 * @since 2012-08-13
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld2", propOrder = { "arg0" })
public class HelloWorld2 implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected String arg0;
    
    /**
     * Returns the input argument for the HelloWorld2 operation.
     *
     * @return String the input argument
     */
    public String getArg0() {
        return this.arg0;
    }
    
    /**
     * Sets the input argument for the HelloWorld2 operation.
     *
     * @param arg0 String the input argument to set
     */
    public void setArg0(final String arg0) {
        this.arg0 = arg0;
    }
}
