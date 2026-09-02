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
@XmlType(name="reportClass")
@XmlEnum
public enum ReportClass {
    @XmlEnumValue("Diagnostic Imaging Report")
    DIAGNOSTIC_IMAGING_REPORT("Diagnostic Imaging Report"),
    @XmlEnumValue("Diagnostic Test Report")
    DIAGNOSTIC_TEST_REPORT("Diagnostic Test Report"),
    @XmlEnumValue("Other Letter")
    OTHER_LETTER("Other Letter"),
    @XmlEnumValue("Consultant Report")
    CONSULTANT_REPORT("Consultant Report"),
    @XmlEnumValue("Medical Records Report")
    MEDICAL_RECORDS_REPORT("Medical Records Report"),
    @XmlEnumValue("Cardio Respiratory Report")
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
