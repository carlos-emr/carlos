package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB generated class for specifies related to Hospital Report Manager XML schema definitions.
 */
@XmlType(name="propertyOfOffendingAgent")
@XmlEnum
public enum PropertyOfOffendingAgent {
    // Represents an element from the HRM XML schema

    DR,
    ND,
    UK;


    public String value() {
        return this.name();
    }

    public static PropertyOfOffendingAgent fromValue(String v) {
        return PropertyOfOffendingAgent.valueOf(v);
    }
}
