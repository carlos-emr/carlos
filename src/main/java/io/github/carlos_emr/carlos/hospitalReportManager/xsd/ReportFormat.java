package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="reportFormat")
@XmlEnum
public enum ReportFormat {
    TEXT("Text"),
    BINARY("Binary");

    private final String value;

    private ReportFormat(String v) {
        this.value = v;
    }

    public String value() {
        return this.value;
    }

    public static ReportFormat fromValue(String v) {
        for (ReportFormat c : ReportFormat.values()) {
            if (!c.value.equals(v)) continue;
            return c;
        }
        throw new IllegalArgumentException(v);
    }
}
