package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * JAXB fault bean indicating that a duplicate Health Insurance Number (HIN) was encountered.
 *
 * <p>Thrown by HNR web service operations when attempting to register a client
 * whose HIN already exists in the registry.
 *
 * @since 2012-08-13
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DuplicateHinException")
public class DuplicateHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
