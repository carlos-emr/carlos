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
@XmlType(name = "cachedFacility", propOrder = { "allowSims", "contactEmail", "contactName", "contactPhone", "description", "enableIntegratedReferrals", "hic", "integratorFacilityId", "lastDataUpdate", "name" })
public class CachedFacility extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected boolean allowSims;
    protected String contactEmail;
    protected String contactName;
    protected String contactPhone;
    protected String description;
    protected boolean enableIntegratedReferrals;
    protected boolean hic;
    protected Integer integratorFacilityId;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar lastDataUpdate;
    protected String name;
    
    public boolean isAllowSims() {
        return this.allowSims;
    }
    
    public void setAllowSims(final boolean allowSims) {
        this.allowSims = allowSims;
    }
    
    public String getContactEmail() {
        return this.contactEmail;
    }
    
    public void setContactEmail(final String contactEmail) {
        this.contactEmail = contactEmail;
    }
    
    public String getContactName() {
        return this.contactName;
    }
    
    public void setContactName(final String contactName) {
        this.contactName = contactName;
    }
    
    public String getContactPhone() {
        return this.contactPhone;
    }
    
    public void setContactPhone(final String contactPhone) {
        this.contactPhone = contactPhone;
    }
    
    public String getDescription() {
        return this.description;
    }
    
    public void setDescription(final String description) {
        this.description = description;
    }
    
    public boolean isEnableIntegratedReferrals() {
        return this.enableIntegratedReferrals;
    }
    
    public void setEnableIntegratedReferrals(final boolean enableIntegratedReferrals) {
        this.enableIntegratedReferrals = enableIntegratedReferrals;
    }
    
    public boolean isHic() {
        return this.hic;
    }
    
    public void setHic(final boolean hic) {
        this.hic = hic;
    }
    
    public Integer getIntegratorFacilityId() {
        return this.integratorFacilityId;
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        this.integratorFacilityId = integratorFacilityId;
    }
    
    public Calendar getLastDataUpdate() {
        return this.lastDataUpdate;
    }
    
    public void setLastDataUpdate(final Calendar lastDataUpdate) {
        this.lastDataUpdate = lastDataUpdate;
    }
    
    public String getName() {
        return this.name;
    }
    
    public void setName(final String name) {
        this.name = name;
    }
}
