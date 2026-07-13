package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.CollapsedStringAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="postalZipCode", propOrder={"postalCode", "zipCode"})
public class PostalZipCode {
    @XmlElement(name="PostalCode")
    protected String postalCode;
    @XmlElement(name="ZipCode")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String zipCode;

    public String getPostalCode() {
        return this.postalCode;
    }

    public void setPostalCode(String value) {
        this.postalCode = value;
    }

    public String getZipCode() {
        return this.zipCode;
    }

    public void setZipCode(String value) {
        this.zipCode = value;
    }
}
