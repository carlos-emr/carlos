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
@XmlType(name="personNamePrefixCode")
@XmlEnum
public enum PersonNamePrefixCode {
    @XmlEnumValue("Miss")
    MISS("Miss"),
    @XmlEnumValue("Mr")
    MR("Mr"),
    @XmlEnumValue("Mssr")
    MSSR("Mssr"),
    @XmlEnumValue("Mrs")
    MRS("Mrs"),
    @XmlEnumValue("Ms")
    MS("Ms"),
    @XmlEnumValue("Prof")
    PROF("Prof"),
    @XmlEnumValue("Reeve")
    REEVE("Reeve"),
    @XmlEnumValue("Rev")
    REV("Rev"),
    @XmlEnumValue("RtHon")
    RT_HON("RtHon"),
    @XmlEnumValue("Sen")
    SEN("Sen"),
    @XmlEnumValue("Sgt")
    SGT("Sgt"),
    @XmlEnumValue("Sr")
    SR("Sr");

    private final String value;

    private PersonNamePrefixCode(String v) {
        this.value = v;
    }

    public String value() {
        return this.value;
    }

    public static PersonNamePrefixCode fromValue(String v) {
        for (PersonNamePrefixCode c : PersonNamePrefixCode.values()) {
            if (!c.value.equals(v)) continue;
            return c;
        }
        throw new IllegalArgumentException(v);
    }
}
