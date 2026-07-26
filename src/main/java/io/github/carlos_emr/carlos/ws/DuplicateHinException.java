package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Exception thrown by web services when attempting to register a patient with an already existing Health Insurance Number.
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DuplicateHinException")

public class DuplicateHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
