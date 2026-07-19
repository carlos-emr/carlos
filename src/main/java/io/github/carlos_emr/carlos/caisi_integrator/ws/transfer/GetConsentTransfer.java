package io.github.carlos_emr.carlos.caisi_integrator.ws.transfer;

import java.util.Date;
import java.io.Serializable;

public class GetConsentTransfer implements Serializable
{
    private Integer integratorFacilityId;
    private ConsentState consentState;
    private Date consentDate;
    
    public Integer getIntegratorFacilityId() {
        return this.integratorFacilityId;
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        this.integratorFacilityId = integratorFacilityId;
    }
    
    public ConsentState getConsentState() {
        return this.consentState;
    }
    
    public void setConsentState(final ConsentState consentState) {
        this.consentState = consentState;
    }
    
    public Date getConsentDate() {
        return this.consentDate;
    }
    
    public void setConsentDate(final Date consentDate) {
        this.consentDate = consentDate;
    }
    
    public enum ConsentState
    {
        ALL, 
        SOME, 
        NONE;
    }
}
