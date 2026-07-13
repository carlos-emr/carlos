package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="adverseReactionSeverity")
@XmlEnum
public enum AdverseReactionSeverity {
    NO,
    MI,
    MO,
    LT;


    public String value() {
        return this.name();
    }

    public static AdverseReactionSeverity fromValue(String v) {
        return AdverseReactionSeverity.valueOf(v);
    }
}
