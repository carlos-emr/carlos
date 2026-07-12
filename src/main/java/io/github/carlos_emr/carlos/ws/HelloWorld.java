package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Primary health check endpoint for the web service layer.
 *
 * <p>Confirms that the SOAP/REST engine is responsive and properly initialized.</p>
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld")
public class HelloWorld implements Serializable
{
    // Endpoint must remain unauthenticated for load balancer health checks
    private static final long serialVersionUID = 1L;
}
