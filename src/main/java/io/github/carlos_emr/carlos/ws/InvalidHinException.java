package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Custom web service exception for handling invalid or duplicate states.
 * Thrown during SOAP/REST payload validation to ensure that downstream services receive structured fault responses
 * without exposing internal stack traces or internal schema details to external clients.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidHinException")
public class InvalidHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
