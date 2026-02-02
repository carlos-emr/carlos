package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="diabetesComplicationScreening", propOrder={"examCode", "date"})
public class DiabetesComplicationScreening {
    @XmlElement(name="ExamCode", required=true)
    protected String examCode;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public String getExamCode() {
        return this.examCode;
    }

    public void setExamCode(String value) {
        this.examCode = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
