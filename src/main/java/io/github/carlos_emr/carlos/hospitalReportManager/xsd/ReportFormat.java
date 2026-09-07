package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;

/*
 * Recovered from the generated cds_hrm JAXB model. The decompiled source lost the per-constant
 * @XmlEnumValue mappings; without them JAXB matches XML values against the constant NAMES, so a
 * schema value of this enum such as "Text" (constant TEXT) unmarshals to null. Keep the
 * annotation on every constant whose XML value differs from its name.
 */
@XmlType(name="reportFormat")
@XmlEnum
public enum ReportFormat {
    @XmlEnumValue("Text")
    TEXT("Text"),
    @XmlEnumValue("Binary")
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
