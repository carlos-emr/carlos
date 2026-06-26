package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB generated class for specifies related to Hospital Report Manager XML schema definitions.
 */
@XmlType(name="addressType")
@XmlEnum
public enum AddressType {
    // Represents an element from the HRM XML schema

    M,
    R;


    public String value() {
        return this.name();
    }

    public static AddressType fromValue(String v) {
        return AddressType.valueOf(v);
    }
}
