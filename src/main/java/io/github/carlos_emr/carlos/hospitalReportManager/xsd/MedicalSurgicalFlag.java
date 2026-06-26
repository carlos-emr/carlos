package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB generated class for specifies related to Hospital Report Manager XML schema definitions.
 */
@XmlType(name="medicalSurgicalFlag")
@XmlEnum
public enum MedicalSurgicalFlag {
    // Represents an element from the HRM XML schema

    M,
    S,
    O,
    P,
    T,
    U;


    public String value() {
        return this.name();
    }

    public static MedicalSurgicalFlag fromValue(String v) {
        return MedicalSurgicalFlag.valueOf(v);
    }
}
