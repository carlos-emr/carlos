package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB generated class for specifies related to Hospital Report Manager XML schema definitions.
 */
@XmlType(name="resultNormalAbnormalFlag")
@XmlEnum
public enum ResultNormalAbnormalFlag {
    // Represents an element from the HRM XML schema

    Y,
    N,
    U;


    public String value() {
        return this.name();
    }

    public static ResultNormalAbnormalFlag fromValue(String v) {
        return ResultNormalAbnormalFlag.valueOf(v);
    }
}
