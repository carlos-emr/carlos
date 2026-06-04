package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * JAXB wrapper for the request payload of the secondary HelloWorld service endpoint.
 * Utilized for enhanced diagnostic ping operations, verifying both the endpoint availability and payload parsing.
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld2", propOrder = { "arg0" })
public class HelloWorld2 implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected String arg0;
    
    public String getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final String arg0) {
        this.arg0 = arg0;
    }
}
