package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="diabetesSelfManagementChallenges", propOrder={"codeValue", "challengesIdentified", "date"})
public class DiabetesSelfManagementChallenges {
    @XmlElement(name="CodeValue", required=true)
    protected String codeValue;
    @XmlElement(name="ChallengesIdentified", required=true)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String challengesIdentified;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public String getCodeValue() {
        return this.codeValue;
    }

    public void setCodeValue(String value) {
        this.codeValue = value;
    }

    public String getChallengesIdentified() {
        return this.challengesIdentified;
    }

    public void setChallengesIdentified(String value) {
        this.challengesIdentified = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
