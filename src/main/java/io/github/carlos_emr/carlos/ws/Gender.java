package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Represents a Gender in the system.
 *
 * @since 2026-06-10
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
