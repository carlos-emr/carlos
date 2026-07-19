package io.github.carlos_emr.carlos.caisi_integrator.ws.transfer;

import java.util.Date;
import java.io.Serializable;

public class SetConsentTransfer implements Serializable
{
    private Integer demographicId;
    private String consentStatus;
    private Date createdDate;
    private boolean excludeMentalHealthData;
    private FacilityConsentPair[] consentToShareData;
    private Date expiry;
    
    public Integer getDemographicId() {
        return this.demographicId;
    }
    
    public void setDemographicId(final Integer demographicId) {
        this.demographicId = demographicId;
    }
    
    public String getConsentStatus() {
        return this.consentStatus;
    }
    
    public void setConsentStatus(final String consentStatus) {
        this.consentStatus = consentStatus;
    }
    
    public Date getCreatedDate() {
        return this.createdDate;
    }
    
    public void setCreatedDate(final Date createdDate) {
        this.createdDate = createdDate;
    }
    
    public boolean isExcludeMentalHealthData() {
        return this.excludeMentalHealthData;
    }
    
    public void setExcludeMentalHealthData(final boolean excludeMentalHealthData) {
        this.excludeMentalHealthData = excludeMentalHealthData;
    }
    
    public FacilityConsentPair[] getConsentToShareData() {
        return this.consentToShareData;
    }
    
    public void setConsentToShareData(final FacilityConsentPair[] consentToShareData) {
        this.consentToShareData = consentToShareData;
    }
    
    public Date getExpiry() {
        return this.expiry;
    }
    
    public void setExpiry(final Date expiry) {
        this.expiry = expiry;
    }
}
