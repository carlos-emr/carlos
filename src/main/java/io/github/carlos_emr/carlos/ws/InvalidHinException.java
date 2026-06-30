package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Exception indicating that the provided Health Insurance Number format or checksum is invalid.
 * Signals failure before database lookups occur.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidHinException")
public class InvalidHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
