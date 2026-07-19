package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "role")
@XmlEnum
public enum Role
{
    PSYCHIATRIST, 
    DOCTOR, 
    RN, 
    CLINICAL_SOCIAL_WORKER, 
    RECREATION_THERAPIST, 
    RPN, 
    NURSE_MANAGER, 
    CLINICAL_CASE_MANAGER, 
    CLINICAL_ASSISTANT, 
    MEDICAL_SECRETARY, 
    HOUSING_WORKER, 
    COUNSELLOR, 
    CASE_MANAGER, 
    SECRETARY, 
    RECEPTIONIST, 
    SUPPORT_WORKER, 
    CLIENT_SERVICE_WORKER, 
    PROPERTY_STAFF, 
    CSW, 
    SUPPORT_COUNSELLOR;
    
    public String value() {
        return this.name();
    }
    
    public static Role fromValue(final String s) {
        return valueOf(s);
    }
}
