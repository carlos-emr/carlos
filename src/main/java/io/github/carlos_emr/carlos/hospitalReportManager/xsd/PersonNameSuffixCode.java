package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;

/*
 * Recovered from the generated cds_hrm JAXB model. The decompiled source lost the per-constant
 * @XmlEnumValue mappings; without them JAXB matches XML values against the constant NAMES, so a
 * schema value of this enum such as "Jr" (constant JR) unmarshals to null. Keep the
 * annotation on every constant whose XML value differs from its name.
 */
@XmlType(name="personNameSuffixCode")
@XmlEnum
public enum PersonNameSuffixCode {
    @XmlEnumValue("Jr")
    JR("Jr"),
    @XmlEnumValue("Sr")
    SR("Sr"),
    @XmlEnumValue("II")
    II("II"),
    @XmlEnumValue("III")
    III("III"),
    @XmlEnumValue("IV")
    IV("IV");

    private final String value;

    private PersonNameSuffixCode(String v) {
        this.value = v;
    }

    public String value() {
        return this.value;
    }

    public static PersonNameSuffixCode fromValue(String v) {
        for (PersonNameSuffixCode c : PersonNameSuffixCode.values()) {
            if (!c.value.equals(v)) continue;
            return c;
        }
        throw new IllegalArgumentException(v);
    }
}
