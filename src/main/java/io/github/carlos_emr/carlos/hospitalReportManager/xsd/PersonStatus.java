package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="personStatus")
@XmlEnum
public enum PersonStatus {
    A,
    I,
    D,
    O;


    public String value() {
        return this.name();
    }

    public static PersonStatus fromValue(String v) {
        return PersonStatus.valueOf(v);
    }
}
