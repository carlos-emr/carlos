package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * JAXB wrapper for the request payload of the initial HelloWorld service endpoint.
 * Serves as a basic ping mechanism to ensure the SOAP/REST web service infrastructure is operational.
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld")
public class HelloWorld implements Serializable
{
    private static final long serialVersionUID = 1L;
}
