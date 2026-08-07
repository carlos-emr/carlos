package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.CollapsedStringAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="diabetesEducationalSelfManagement", propOrder={"educationalTrainingPerformed", "date"})
public class DiabetesEducationalSelfManagement {
    @XmlElement(name="EducationalTrainingPerformed", required=true)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String educationalTrainingPerformed;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public String getEducationalTrainingPerformed() {
        return this.educationalTrainingPerformed;
    }

    public void setEducationalTrainingPerformed(String value) {
        this.educationalTrainingPerformed = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
