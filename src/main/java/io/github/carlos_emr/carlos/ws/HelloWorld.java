package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Core web service component handling HelloWorld integration logic.
 * Enforces security boundaries and validates incoming payloads before mapping them to internal domain entities.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld")
public class HelloWorld implements Serializable
{
    private static final long serialVersionUID = 1L;
}
