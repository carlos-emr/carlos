package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PostalZipCode;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="address.structured", propOrder={"line1", "line2", "line3", "city", "countrySubdivisionCode", "postalZipCode"})
public class AddressStructured {
    @XmlElement(name="Line1", required=true)
    protected String line1;
    @XmlElement(name="Line2")
    protected String line2;
    @XmlElement(name="Line3")
    protected String line3;
    @XmlElement(name="City")
    protected String city;
    @XmlElement(name="CountrySubdivisionCode")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String countrySubdivisionCode;
    @XmlElement(name="PostalZipCode")
    protected PostalZipCode postalZipCode;

    public String getLine1() {
        return this.line1;
    }

    public void setLine1(String value) {
        this.line1 = value;
    }

    public String getLine2() {
        return this.line2;
    }

    public void setLine2(String value) {
        this.line2 = value;
    }

    public String getLine3() {
        return this.line3;
    }

    public void setLine3(String value) {
        this.line3 = value;
    }

    public String getCity() {
        return this.city;
    }

    public void setCity(String value) {
        this.city = value;
    }

    public String getCountrySubdivisionCode() {
        return this.countrySubdivisionCode;
    }

    public void setCountrySubdivisionCode(String value) {
        this.countrySubdivisionCode = value;
    }

    public PostalZipCode getPostalZipCode() {
        return this.postalZipCode;
    }

    public void setPostalZipCode(PostalZipCode value) {
        this.postalZipCode = value;
    }
}
