package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Exception thrown when a Health Insurance Number (HIN) already exists in the system.
 *
 * <p>Prevents the creation of duplicate demographic records, ensuring data integrity
 * across the patient registry.</p>
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DuplicateHinException")
public class DuplicateHinException implements Serializable
{
    // Surface a user-friendly error instructing the user to merge records if necessary
    private static final long serialVersionUID = 1L;
}
