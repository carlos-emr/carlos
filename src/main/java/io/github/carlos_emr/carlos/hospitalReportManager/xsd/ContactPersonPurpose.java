package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlType(name="contactPersonPurpose")
@XmlEnum
public enum ContactPersonPurpose {
    EC,
    NK,
    AS,
    CG,
    PA,
    IN,
    GT,
    O;


    public String value() {
        return this.name();
    }

    public static ContactPersonPurpose fromValue(String v) {
        return ContactPersonPurpose.valueOf(v);
    }
}
