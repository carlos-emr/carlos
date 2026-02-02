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
@XmlType(name = "cachedAdmission", propOrder = { "admissionDate", "admissionNotes", "caisiDemographicId", "caisiProgramId", "dischargeDate", "dischargeNotes", "facilityIdIntegerCompositePk" })
public class CachedAdmission extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar admissionDate;
    protected String admissionNotes;
    protected int caisiDemographicId;
    protected int caisiProgramId;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar dischargeDate;
    protected String dischargeNotes;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    
    public Calendar getAdmissionDate() {
        return this.admissionDate;
    }
    
    public void setAdmissionDate(final Calendar admissionDate) {
        this.admissionDate = admissionDate;
    }
    
    public String getAdmissionNotes() {
        return this.admissionNotes;
    }
    
    public void setAdmissionNotes(final String admissionNotes) {
        this.admissionNotes = admissionNotes;
    }
    
    public int getCaisiDemographicId() {
        return this.caisiDemographicId;
    }
    
    public void setCaisiDemographicId(final int caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }
    
    public int getCaisiProgramId() {
        return this.caisiProgramId;
    }
    
    public void setCaisiProgramId(final int caisiProgramId) {
        this.caisiProgramId = caisiProgramId;
    }
    
    public Calendar getDischargeDate() {
        return this.dischargeDate;
    }
    
    public void setDischargeDate(final Calendar dischargeDate) {
        this.dischargeDate = dischargeDate;
    }
    
    public String getDischargeNotes() {
        return this.dischargeNotes;
    }
    
    public void setDischargeNotes(final String dischargeNotes) {
        this.dischargeNotes = dischargeNotes;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }
}
