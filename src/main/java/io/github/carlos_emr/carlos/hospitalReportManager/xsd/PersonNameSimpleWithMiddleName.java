package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="personNameSimpleWithMiddleName", propOrder={"firstName", "middleName", "lastName"})
public class PersonNameSimpleWithMiddleName {
    @XmlElement(name="FirstName")
    protected String firstName;
    @XmlElement(name="MiddleName")
    protected String middleName;
    @XmlElement(name="LastName")
    protected String lastName;

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(String value) {
        this.firstName = value;
    }

    public String getMiddleName() {
        return this.middleName;
    }

    public void setMiddleName(String value) {
        this.middleName = value;
    }

    public String getLastName() {
        return this.lastName;
    }

    public void setLastName(String value) {
        this.lastName = value;
    }
}
