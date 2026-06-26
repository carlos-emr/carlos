package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB generated class for specifies related to Hospital Report Manager XML schema definitions.
 */
@XmlType(name="phoneNumberType")
@XmlEnum
public enum PhoneNumberType {
    // Represents an element from the HRM XML schema

    R,
    C,
    W;


    public String value() {
        return this.name();
    }

    public static PhoneNumberType fromValue(String v) {
        return PhoneNumberType.valueOf(v);
    }
}
