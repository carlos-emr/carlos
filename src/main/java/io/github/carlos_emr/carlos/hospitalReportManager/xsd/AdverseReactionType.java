package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB generated class for specifies related to Hospital Report Manager XML schema definitions.
 */
@XmlType(name="adverseReactionType")
@XmlEnum
public enum AdverseReactionType {
    // Represents an element from the HRM XML schema

    AL,
    AR;


    public String value() {
        return this.name();
    }

    public static AdverseReactionType fromValue(String v) {
        return AdverseReactionType.valueOf(v);
    }
}
