package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "helloWorld")
public class HelloWorld implements Serializable
{
    private static final long serialVersionUID = 1L;
}
