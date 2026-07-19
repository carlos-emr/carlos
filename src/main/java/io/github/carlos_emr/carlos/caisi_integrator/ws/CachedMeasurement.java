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
@XmlType(name = "cachedMeasurement", propOrder = { "caisiDemographicId", "caisiProviderId", "comments", "dataField", "dateEntered", "dateObserved", "facilityIdIntegerCompositePk", "measuringInstruction", "type" })
public class CachedMeasurement extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer caisiDemographicId;
    protected String caisiProviderId;
    protected String comments;
    protected String dataField;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar dateEntered;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar dateObserved;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String measuringInstruction;
    protected String type;
    
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
    
    public String getComments() {
        return this.comments;
    }
    
    public void setComments(final String comments) {
        this.comments = comments;
    }
    
    public String getDataField() {
        return this.dataField;
    }
    
    public void setDataField(final String dataField) {
        this.dataField = dataField;
    }
    
    public Calendar getDateEntered() {
        return this.dateEntered;
    }
    
    public void setDateEntered(final Calendar dateEntered) {
        this.dateEntered = dateEntered;
    }
    
    public Calendar getDateObserved() {
        return this.dateObserved;
    }
    
    public void setDateObserved(final Calendar dateObserved) {
        this.dateObserved = dateObserved;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }
    
    public String getMeasuringInstruction() {
        return this.measuringInstruction;
    }
    
    public void setMeasuringInstruction(final String measuringInstruction) {
        this.measuringInstruction = measuringInstruction;
    }
    
    public String getType() {
        return this.type;
    }
    
    public void setType(final String type) {
        this.type = type;
    }
}
