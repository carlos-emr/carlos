package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="bloodPressure", propOrder={"systolicBP", "diastolicBP", "bpUnit", "date"})
public class BloodPressure {
    @XmlElement(name="SystolicBP", required=true)
    protected String systolicBP;
    @XmlElement(name="DiastolicBP", required=true)
    protected String diastolicBP;
    @XmlElement(name="BPUnit", required=true)
    protected String bpUnit;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public String getSystolicBP() {
        return this.systolicBP;
    }

    public void setSystolicBP(String value) {
        this.systolicBP = value;
    }

    public String getDiastolicBP() {
        return this.diastolicBP;
    }

    public void setDiastolicBP(String value) {
        this.diastolicBP = value;
    }

    public String getBPUnit() {
        return this.bpUnit;
    }

    public void setBPUnit(String value) {
        this.bpUnit = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
