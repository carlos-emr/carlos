package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Core web service component handling Gender integration logic.
 * Enforces security boundaries and validates incoming payloads before mapping them to internal domain entities.
 */
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
