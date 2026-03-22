package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * JAXB fault bean indicating that an invalid Health Insurance Number (HIN) was provided.
 *
 * <p>Thrown by HNR web service operations when the provided HIN does not pass
 * provincial validation rules (format, check digit, etc.).
 *
 * @since 2012-08-13
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidHinException")
public class InvalidHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
