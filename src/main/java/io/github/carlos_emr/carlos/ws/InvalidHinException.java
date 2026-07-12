package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Exception thrown when a Health Insurance Number (HIN) fails validation.
 *
 * <p>Ensures that only properly formatted provincial health numbers are
 * persisted to the database.</p>
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidHinException")
public class InvalidHinException implements Serializable
{
    // Ensure error messages do not echo the invalid HIN to prevent logging sensitive data
    private static final long serialVersionUID = 1L;
}
