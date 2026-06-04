package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Exception thrown during web service operations when an provided Health Insurance Number (HIN)
 * fails checksum validation or format verification (e.g., invalid OHIP number length or characters).
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidHinException")
public class InvalidHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
