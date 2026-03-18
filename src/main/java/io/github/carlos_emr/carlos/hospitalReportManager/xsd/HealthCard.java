package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="healthCard", propOrder={})
public class HealthCard {
    @XmlElement(name="Number", required=true)
    protected String number;
    @XmlElement(name="Version")
    protected String version;
    @XmlElement(name="Expirydate")
    protected XMLGregorianCalendar expirydate;
    @XmlElement(name="ProvinceCode", required=true)
    protected String provinceCode;

    public String getNumber() {
        return this.number;
    }

    public void setNumber(String value) {
        this.number = value;
    }

    public String getVersion() {
        return this.version;
    }

    public void setVersion(String value) {
        this.version = value;
    }

    public XMLGregorianCalendar getExpirydate() {
        return this.expirydate;
    }

    public void setExpirydate(XMLGregorianCalendar value) {
        this.expirydate = value;
    }

    public String getProvinceCode() {
        return this.provinceCode;
    }

    public void setProvinceCode(String value) {
        this.provinceCode = value;
    }
}
