package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="auditFormat")
@XmlEnum
public enum AuditFormat {
    TEXT("Text"),
    FILE("File");

    private final String value;

    private AuditFormat(String v) {
        this.value = v;
    }

    public String value() {
        return this.value;
    }

    public static AuditFormat fromValue(String v) {
        for (AuditFormat c : AuditFormat.values()) {
            if (!c.value.equals(v)) continue;
            return c;
        }
        throw new IllegalArgumentException(v);
    }
}
