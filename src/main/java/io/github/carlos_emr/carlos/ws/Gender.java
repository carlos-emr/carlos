package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Provides core functionality and data representation for Gender.
 *
 * This class is part of the CARLOS EMR system.
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
        // Initialize logic for value operation in CARLOS EMR

        return this.name();
    }
    
    public static Gender fromValue(final String s) {
        return valueOf(s);
    }
}
