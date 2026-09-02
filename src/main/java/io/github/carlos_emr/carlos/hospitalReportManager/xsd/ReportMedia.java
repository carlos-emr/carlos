package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;

/*
 * Recovered from the generated cds_hrm JAXB model. The decompiled source lost the per-constant
 * @XmlEnumValue mappings; without them JAXB matches XML values against the constant NAMES, every
 * schema value such as "Diagnostic Imaging Report" unmarshals to null, and HRMReport NPEs on the
 * first report it lists. Keep the annotation on every constant whose XML value differs from its name.
 */
@XmlType(name="reportMedia")
@XmlEnum
public enum ReportMedia {
    @XmlEnumValue("Email")
    EMAIL("Email"),
    @XmlEnumValue("Download")
    DOWNLOAD("Download"),
    @XmlEnumValue("Portable Media")
    PORTABLE_MEDIA("Portable Media"),
    @XmlEnumValue("Hardcopy")
    HARDCOPY("Hardcopy");

    private final String value;

    private ReportMedia(String v) {
        this.value = v;
    }

    public String value() {
        return this.value;
    }

    public static ReportMedia fromValue(String v) {
        for (ReportMedia c : ReportMedia.values()) {
            if (!c.value.equals(v)) continue;
            return c;
        }
        throw new IllegalArgumentException(v);
    }
}
