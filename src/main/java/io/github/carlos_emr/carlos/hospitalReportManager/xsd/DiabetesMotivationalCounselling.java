package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="diabetesMotivationalCounselling", propOrder={"counsellingPerformed", "date"})
public class DiabetesMotivationalCounselling {
    @XmlElement(name="CounsellingPerformed", required=true)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String counsellingPerformed;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public String getCounsellingPerformed() {
        return this.counsellingPerformed;
    }

    public void setCounsellingPerformed(String value) {
        this.counsellingPerformed = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
