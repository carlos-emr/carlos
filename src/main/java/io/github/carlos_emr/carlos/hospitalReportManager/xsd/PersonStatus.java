package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB generated class for specifies related to Hospital Report Manager XML schema definitions.
 */
@XmlType(name="personStatus")
@XmlEnum
public enum PersonStatus {
    // Represents an element from the HRM XML schema

    A,
    I,
    D,
    O;


    public String value() {
        return this.name();
    }

    public static PersonStatus fromValue(String v) {
        return PersonStatus.valueOf(v);
    }
}
