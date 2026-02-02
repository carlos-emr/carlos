package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import org.w3._2001.xmlschema.Adapter1;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.XmlElement;
import java.util.Calendar;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getConsentTransfer", propOrder = { "consentDate", "consentState", "integratorFacilityId" })
public class GetConsentTransfer implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar consentDate;
    @XmlSchemaType(name = "string")
    protected ConsentState consentState;
    protected Integer integratorFacilityId;
    
    public Calendar getConsentDate() {
        return this.consentDate;
    }
    
    public void setConsentDate(final Calendar consentDate) {
        this.consentDate = consentDate;
    }
    
    public ConsentState getConsentState() {
        return this.consentState;
    }
    
    public void setConsentState(final ConsentState consentState) {
        this.consentState = consentState;
    }
    
    public Integer getIntegratorFacilityId() {
        return this.integratorFacilityId;
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        this.integratorFacilityId = integratorFacilityId;
    }
}
