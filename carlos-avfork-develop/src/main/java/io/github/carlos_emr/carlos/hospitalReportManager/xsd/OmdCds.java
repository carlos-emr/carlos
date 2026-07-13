package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PatientRecord;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="", propOrder={"patientRecord"})
@XmlRootElement(name="OmdCds", namespace="cds")
public class OmdCds {
    @XmlElement(name="PatientRecord", namespace="cds", required=true)
    protected PatientRecord patientRecord;

    public PatientRecord getPatientRecord() {
        return this.patientRecord;
    }

    public void setPatientRecord(PatientRecord value) {
        this.patientRecord = value;
    }
}
