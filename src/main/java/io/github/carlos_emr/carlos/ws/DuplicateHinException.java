package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Exception thrown for DuplicateHin errors.
 *
 * This class is part of the CARLOS EMR system.
 */


@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DuplicateHinException")
public class DuplicateHinException implements Serializable
{
    private static final long serialVersionUID = 1L;
}
