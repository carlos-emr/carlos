package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Secondary health check endpoint for the web service layer.
 *
 * <p>Used for verifying specific payload processing and XML unmarshalling.</p>
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld2", propOrder = { "arg0" })
    // Endpoint must remain unauthenticated for load balancer health checks
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
