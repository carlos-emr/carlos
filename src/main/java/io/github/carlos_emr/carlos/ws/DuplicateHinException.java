package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Exception thrown during web service operations when an attempt is made to register or update
 * a patient with a Health Insurance Number (HIN) that already exists in the system.
 * Used to enforce strict HIN uniqueness across demographics.
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DuplicateHinException")
public class DuplicateHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
