package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "consentState")
@XmlEnum
public enum ConsentState
{
    ALL, 
    SOME, 
    NONE;
    
    public String value() {
        return this.name();
    }
    
    public static ConsentState fromValue(final String s) {
        return valueOf(s);
    }
}
