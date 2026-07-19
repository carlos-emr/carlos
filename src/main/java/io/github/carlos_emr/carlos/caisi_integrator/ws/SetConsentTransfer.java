package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlSchemaType;
import org.w3._2001.xmlschema.Adapter1;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Calendar;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "setConsentTransfer", propOrder = { "consentStatus", "consentToShareData", "createdDate", "demographicId", "excludeMentalHealthData", "expiry" })
public class SetConsentTransfer implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected String consentStatus;
    @XmlElement(nillable = true)
    protected List<FacilityConsentPair> consentToShareData;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar createdDate;
    protected Integer demographicId;
    protected boolean excludeMentalHealthData;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar expiry;
    
    public String getConsentStatus() {
        return this.consentStatus;
    }
    
    public void setConsentStatus(final String consentStatus) {
        this.consentStatus = consentStatus;
    }
    
    public List<FacilityConsentPair> getConsentToShareData() {
        if (this.consentToShareData == null) {
            this.consentToShareData = new ArrayList<FacilityConsentPair>();
        }
        return this.consentToShareData;
    }
    
    public Calendar getCreatedDate() {
        return this.createdDate;
    }
    
    public void setCreatedDate(final Calendar createdDate) {
        this.createdDate = createdDate;
    }
    
    public Integer getDemographicId() {
        return this.demographicId;
    }
    
    public void setDemographicId(final Integer demographicId) {
        this.demographicId = demographicId;
    }
    
    public boolean isExcludeMentalHealthData() {
        return this.excludeMentalHealthData;
    }
    
    public void setExcludeMentalHealthData(final boolean excludeMentalHealthData) {
        this.excludeMentalHealthData = excludeMentalHealthData;
    }
    
    public Calendar getExpiry() {
        return this.expiry;
    }
    
    public void setExpiry(final Calendar expiry) {
        this.expiry = expiry;
    }
}
