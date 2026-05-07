package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Simple diagnostic web service endpoint.
 * <p>
 * Used to verify network connectivity and proper deployment of the SOAP/REST service stack.
 *
 * @since 2026-05-05
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld")
public class HelloWorld implements Serializable
{
    private static final long serialVersionUID = 1L;
}
