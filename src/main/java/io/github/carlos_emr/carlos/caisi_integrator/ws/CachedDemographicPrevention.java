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
@XmlType(name = "cachedDemographicPrevention", propOrder = { "attributes", "caisiDemographicId", "caisiProviderId", "facilityPreventionPk", "never", "nextDate", "preventionDate", "preventionType", "refused" })
public class CachedDemographicPrevention extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected String attributes;
    protected Integer caisiDemographicId;
    protected String caisiProviderId;
    protected FacilityIdIntegerCompositePk facilityPreventionPk;
    protected boolean never;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar nextDate;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar preventionDate;
    protected String preventionType;
    protected boolean refused;
    
    public String getAttributes() {
        return this.attributes;
    }
    
    public void setAttributes(final String attributes) {
        this.attributes = attributes;
    }
    
    public Integer getCaisiDemographicId() {
        return this.caisiDemographicId;
    }
    
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }
    
    public String getCaisiProviderId() {
        return this.caisiProviderId;
    }
    
    public void setCaisiProviderId(final String caisiProviderId) {
        this.caisiProviderId = caisiProviderId;
    }
    
    public FacilityIdIntegerCompositePk getFacilityPreventionPk() {
        return this.facilityPreventionPk;
    }
    
    public void setFacilityPreventionPk(final FacilityIdIntegerCompositePk facilityPreventionPk) {
        this.facilityPreventionPk = facilityPreventionPk;
    }
    
    public boolean isNever() {
        return this.never;
    }
    
    public void setNever(final boolean never) {
        this.never = never;
    }
    
    public Calendar getNextDate() {
        return this.nextDate;
    }
    
    public void setNextDate(final Calendar nextDate) {
        this.nextDate = nextDate;
    }
    
    public Calendar getPreventionDate() {
        return this.preventionDate;
    }
    
    public void setPreventionDate(final Calendar preventionDate) {
        this.preventionDate = preventionDate;
    }
    
    public String getPreventionType() {
        return this.preventionType;
    }
    
    public void setPreventionType(final String preventionType) {
        this.preventionType = preventionType;
    }
    
    public boolean isRefused() {
        return this.refused;
    }
    
    public void setRefused(final boolean refused) {
        this.refused = refused;
    }
}
