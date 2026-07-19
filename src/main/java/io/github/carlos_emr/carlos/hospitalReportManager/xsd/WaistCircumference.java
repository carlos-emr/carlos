package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="waistCircumference", propOrder={"waistCircumference", "waistCircumferenceUnit", "date"})
public class WaistCircumference {
    @XmlElement(name="WaistCircumference", required=true)
    protected String waistCircumference;
    @XmlElement(name="WaistCircumferenceUnit", required=true)
    protected String waistCircumferenceUnit;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public String getWaistCircumference() {
        return this.waistCircumference;
    }

    public void setWaistCircumference(String value) {
        this.waistCircumference = value;
    }

    public String getWaistCircumferenceUnit() {
        return this.waistCircumferenceUnit;
    }

    public void setWaistCircumferenceUnit(String value) {
        this.waistCircumferenceUnit = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
