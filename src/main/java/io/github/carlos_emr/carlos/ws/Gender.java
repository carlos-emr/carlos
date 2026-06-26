package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Web service endpoint or data contract for Gender operations.
 */
@XmlType(name = "gender")
@XmlEnum
public enum Gender
{
    // Manages web service payload for Gender

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
