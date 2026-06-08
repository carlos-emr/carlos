package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Data Transfer Object representing the payload for the HelloWorld web service request.
 * Encapsulates the parameters provided by the SOAP/REST client.
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld")
public class HelloWorld implements Serializable
{
    // Unique identifier for reliable serialization across different JVMs
    private static final long serialVersionUID = 1L;
}
