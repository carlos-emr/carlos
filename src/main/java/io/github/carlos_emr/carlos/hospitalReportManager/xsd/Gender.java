package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB generated class for specifies related to Hospital Report Manager XML schema definitions.
 */
@XmlType(name="gender")
@XmlEnum
public enum Gender {
    // Represents an element from the HRM XML schema

    M,
    F,
    O,
    U;


    public String value() {
        return this.name();
    }

    public static Gender fromValue(String v) {
        return Gender.valueOf(v);
    }
}
