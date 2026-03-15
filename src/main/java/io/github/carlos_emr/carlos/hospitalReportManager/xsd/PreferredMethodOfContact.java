package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="preferredMethodOfContact")
@XmlEnum
public enum PreferredMethodOfContact {
    B,
    C,
    E,
    F,
    H,
    O;


    public String value() {
        return this.name();
    }

    public static PreferredMethodOfContact fromValue(String v) {
        return PreferredMethodOfContact.valueOf(v);
    }
}
