package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="personNameSuffixCode")
@XmlEnum
public enum PersonNameSuffixCode {
    JR("Jr"),
    SR("Sr"),
    II("II"),
    III("III"),
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
