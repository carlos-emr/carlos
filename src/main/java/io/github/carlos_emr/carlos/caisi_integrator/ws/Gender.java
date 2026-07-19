package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "gender")
@XmlEnum
public enum Gender
{
    M, 
    F, 
    T, 
    O, 
    U;
    
    public String value() {
        return this.name();
    }
    
    public static Gender fromValue(final String s) {
        return valueOf(s);
    }
}
