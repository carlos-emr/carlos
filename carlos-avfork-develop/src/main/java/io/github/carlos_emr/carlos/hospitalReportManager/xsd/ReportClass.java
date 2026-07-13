package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="reportClass")
@XmlEnum
public enum ReportClass {
    DIAGNOSTIC_IMAGING_REPORT("Diagnostic Imaging Report"),
    DIAGNOSTIC_TEST_REPORT("Diagnostic Test Report"),
    OTHER_LETTER("Other Letter"),
    CONSULTANT_REPORT("Consultant Report"),
    MEDICAL_RECORDS_REPORT("Medical Records Report"),
    CARDIO_RESPIRATORY_REPORT("Cardio Respiratory Report");

    private final String value;

    private ReportClass(String v) {
        this.value = v;
    }

    public String value() {
        return this.value;
    }

    public static ReportClass fromValue(String v) {
        for (ReportClass c : ReportClass.values()) {
            if (!c.value.equals(v)) continue;
            return c;
        }
        throw new IllegalArgumentException(v);
    }
}
