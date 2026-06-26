package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB generated class for specifies related to Hospital Report Manager XML schema definitions.
 */
@XmlType(name="personNamePurposeCode")
@XmlEnum
public enum PersonNamePurposeCode {
    // Represents an element from the HRM XML schema

    HC,
    L,
    AL,
    C;


    public String value() {
        return this.name();
    }

    public static PersonNamePurposeCode fromValue(String v) {
        return PersonNamePurposeCode.valueOf(v);
    }
}
