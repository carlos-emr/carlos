package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Custom exception thrown when a specific InvalidHin error occurs.
 * This allows upstream handlers to catch and format the InvalidHinException appropriately for the client.
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidHinException")
public class InvalidHinException implements Serializable
{
    // Unique identifier for reliable serialization across different JVMs
    private static final long serialVersionUID = 1L;
}
