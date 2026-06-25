package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Represents the standard gender administrative classifications used across the EMR.
 * Values align with standard provincial reporting and HL7 messaging requirements.
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
