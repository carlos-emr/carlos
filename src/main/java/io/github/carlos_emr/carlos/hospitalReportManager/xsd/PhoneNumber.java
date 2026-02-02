package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementRefs;
import javax.xml.bind.annotation.XmlType;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PhoneNumberType;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="phoneNumber", propOrder={"content"})
public class PhoneNumber {
    @XmlElementRefs(value={@XmlElementRef(name="number", namespace="cds_dt", type=JAXBElement.class), @XmlElementRef(name="phoneNumber", namespace="cds_dt", type=JAXBElement.class), @XmlElementRef(name="exchange", namespace="cds_dt", type=JAXBElement.class), @XmlElementRef(name="areaCode", namespace="cds_dt", type=JAXBElement.class), @XmlElementRef(name="extension", namespace="cds_dt", type=JAXBElement.class)})
    protected List<JAXBElement<String>> content;
    @XmlAttribute(required=true)
    protected PhoneNumberType phoneNumberType;

    public List<JAXBElement<String>> getContent() {
        if (this.content == null) {
            this.content = new ArrayList<JAXBElement<String>>();
        }
        return this.content;
    }

    public PhoneNumberType getPhoneNumberType() {
        return this.phoneNumberType;
    }

    public void setPhoneNumberType(PhoneNumberType value) {
        this.phoneNumberType = value;
    }
}
