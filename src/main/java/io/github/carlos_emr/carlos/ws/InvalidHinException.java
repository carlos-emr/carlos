package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Exception thrown when a Health Insurance Number (HIN) fails validation checks.
 * <p>
 * Validation failures may include incorrect format, invalid checksums, or jurisdictional mismatches.
 *
 * @since 2026-05-05
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidHinException")
public class InvalidHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
