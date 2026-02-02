package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="diabetesSelfManagementCollaborative", propOrder={"codeValue", "documentedGoals", "date"})
public class DiabetesSelfManagementCollaborative {
    @XmlElement(name="CodeValue", required=true)
    protected String codeValue;
    @XmlElement(name="DocumentedGoals", required=true)
    protected String documentedGoals;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public String getCodeValue() {
        return this.codeValue;
    }

    public void setCodeValue(String value) {
        this.codeValue = value;
    }

    public String getDocumentedGoals() {
        return this.documentedGoals;
    }

    public void setDocumentedGoals(String value) {
        this.documentedGoals = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
