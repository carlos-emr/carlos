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
@XmlType(name = "cachedDxresearch", propOrder = { "caisiDemographicId", "codingSystem", "dxresearchCode", "facilityIdIntegerCompositePk", "startDate", "status", "updateDate" })
public class CachedDxresearch extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer caisiDemographicId;
    protected String codingSystem;
    protected String dxresearchCode;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar startDate;
    protected String status;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar updateDate;
    
    public Integer getCaisiDemographicId() {
        return this.caisiDemographicId;
    }
    
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }
    
    public String getCodingSystem() {
        return this.codingSystem;
    }
    
    public void setCodingSystem(final String codingSystem) {
        this.codingSystem = codingSystem;
    }
    
    public String getDxresearchCode() {
        return this.dxresearchCode;
    }
    
    public void setDxresearchCode(final String dxresearchCode) {
        this.dxresearchCode = dxresearchCode;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }
    
    public Calendar getStartDate() {
        return this.startDate;
    }
    
    public void setStartDate(final Calendar startDate) {
        this.startDate = startDate;
    }
    
    public String getStatus() {
        return this.status;
    }
    
    public void setStatus(final String status) {
        this.status = status;
    }
    
    public Calendar getUpdateDate() {
        return this.updateDate;
    }
    
    public void setUpdateDate(final Calendar updateDate) {
        this.updateDate = updateDate;
    }
}
