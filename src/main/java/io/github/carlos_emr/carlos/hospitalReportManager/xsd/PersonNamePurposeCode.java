package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="personNamePurposeCode")
@XmlEnum
public enum PersonNamePurposeCode {
    HC,
    L,
    AL,
    C;


    public String value() {
        return this.name();
    }

    public static PersonNamePurposeCode fromValue(String v) {
        return PersonNamePurposeCode.valueOf(v);
    }
}
