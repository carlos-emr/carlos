package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.AddressStructured;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.AddressType;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="address", propOrder={"formatted", "structured"})
public class Address {
    @XmlElement(name="Formatted")
    protected String formatted;
    @XmlElement(name="Structured")
    protected AddressStructured structured;
    @XmlAttribute(required=true)
    protected AddressType addressType;

    public String getFormatted() {
        return this.formatted;
    }

    public void setFormatted(String value) {
        this.formatted = value;
    }

    public AddressStructured getStructured() {
        return this.structured;
    }

    public void setStructured(AddressStructured value) {
        this.structured = value;
    }

    public AddressType getAddressType() {
        return this.addressType;
    }

    public void setAddressType(AddressType value) {
        this.addressType = value;
    }
}
