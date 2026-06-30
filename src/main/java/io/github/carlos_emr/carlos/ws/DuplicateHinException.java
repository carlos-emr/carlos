package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Exception thrown when attempting to register or update a client with an existing Health Insurance Number.
 * Indicates a collision in the patient registry.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DuplicateHinException")
public class DuplicateHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
