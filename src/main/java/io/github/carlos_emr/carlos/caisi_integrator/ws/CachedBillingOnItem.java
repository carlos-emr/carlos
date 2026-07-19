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
@XmlType(name = "cachedBillingOnItem", propOrder = { "appointmentId", "apptProviderId", "asstProviderId", "caisiDemographicId", "caisiProviderId", "dx", "dx1", "dx2", "facilityIdIntegerCompositePk", "serviceCode", "serviceDate", "status" })
public class CachedBillingOnItem extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer appointmentId;
    protected String apptProviderId;
    protected String asstProviderId;
    protected Integer caisiDemographicId;
    protected String caisiProviderId;
    protected String dx;
    protected String dx1;
    protected String dx2;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String serviceCode;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar serviceDate;
    protected String status;
    
    public Integer getAppointmentId() {
        return this.appointmentId;
    }
    
    public void setAppointmentId(final Integer appointmentId) {
        this.appointmentId = appointmentId;
    }
    
    public String getApptProviderId() {
        return this.apptProviderId;
    }
    
    public void setApptProviderId(final String apptProviderId) {
        this.apptProviderId = apptProviderId;
    }
    
    public String getAsstProviderId() {
        return this.asstProviderId;
    }
    
    public void setAsstProviderId(final String asstProviderId) {
        this.asstProviderId = asstProviderId;
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
    
    public String getDx() {
        return this.dx;
    }
    
    public void setDx(final String dx) {
        this.dx = dx;
    }
    
    public String getDx1() {
        return this.dx1;
    }
    
    public void setDx1(final String dx1) {
        this.dx1 = dx1;
    }
    
    public String getDx2() {
        return this.dx2;
    }
    
    public void setDx2(final String dx2) {
        this.dx2 = dx2;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }
    
    public String getServiceCode() {
        return this.serviceCode;
    }
    
    public void setServiceCode(final String serviceCode) {
        this.serviceCode = serviceCode;
    }
    
    public Calendar getServiceDate() {
        return this.serviceDate;
    }
    
    public void setServiceDate(final Calendar serviceDate) {
        this.serviceDate = serviceDate;
    }
    
    public String getStatus() {
        return this.status;
    }
    
    public void setStatus(final String status) {
        this.status = status;
    }
}
