package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Web service endpoint or data contract for HelloWorld operations.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld")
public class HelloWorld implements Serializable
{
    // Manages web service payload for HelloWorld

    private static final long serialVersionUID = 1L;
}
