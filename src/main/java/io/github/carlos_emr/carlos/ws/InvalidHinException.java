package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Exception thrown when an invalid HIN is provided.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidHinException")
public class InvalidHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
