package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="personNamePrefixCode")
@XmlEnum
public enum PersonNamePrefixCode {
    MISS("Miss"),
    MR("Mr"),
    MSSR("Mssr"),
    MRS("Mrs"),
    MS("Ms"),
    PROF("Prof"),
    REEVE("Reeve"),
    REV("Rev"),
    RT_HON("RtHon"),
    SEN("Sen"),
    SGT("Sgt"),
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
