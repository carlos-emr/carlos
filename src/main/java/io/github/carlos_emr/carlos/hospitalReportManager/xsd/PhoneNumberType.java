package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="phoneNumberType")
@XmlEnum
public enum PhoneNumberType {
    R,
    C,
    W;


    public String value() {
        return this.name();
    }

    public static PhoneNumberType fromValue(String v) {
        return PhoneNumberType.valueOf(v);
    }
}
