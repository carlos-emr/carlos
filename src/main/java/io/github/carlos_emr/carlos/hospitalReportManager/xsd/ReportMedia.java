package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="reportMedia")
@XmlEnum
public enum ReportMedia {
    EMAIL("Email"),
    DOWNLOAD("Download"),
    PORTABLE_MEDIA("Portable Media"),
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
