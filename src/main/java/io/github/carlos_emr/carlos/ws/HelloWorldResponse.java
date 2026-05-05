package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * HelloWorldResponse provides functionality and data models for the HelloWorldResponse domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorldResponse", propOrder = { "_return" })
public class HelloWorldResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected String _return;
    
    public String getReturn() {
        return this._return;
    }
    
    public void setReturn(final String return1) {
        this._return = return1;
    }
}
