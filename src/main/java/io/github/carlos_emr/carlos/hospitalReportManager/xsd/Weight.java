package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="weight", propOrder={"weight", "weightUnit", "date"})
public class Weight {
    @XmlElement(name="Weight", required=true)
    protected String weight;
    @XmlElement(name="WeightUnit", required=true)
    protected String weightUnit;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public String getWeight() {
        return this.weight;
    }

    public void setWeight(String value) {
        this.weight = value;
    }

    public String getWeightUnit() {
        return this.weightUnit;
    }

    public void setWeightUnit(String value) {
        this.weightUnit = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
