package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="height", propOrder={"height", "heightUnit", "date"})
public class Height {
    @XmlElement(name="Height", required=true)
    protected String height;
    @XmlElement(name="HeightUnit", required=true)
    protected String heightUnit;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public String getHeight() {
        return this.height;
    }

    public void setHeight(String value) {
        this.height = value;
    }

    public String getHeightUnit() {
        return this.heightUnit;
    }

    public void setHeightUnit(String value) {
        this.heightUnit = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
