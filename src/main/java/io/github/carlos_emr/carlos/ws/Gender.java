package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Gender provides functionality and data models for the Gender domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
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
