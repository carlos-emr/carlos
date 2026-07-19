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
@XmlType(name = "cachedEformData", propOrder = { "caisiDemographicId", "facilityIdIntegerCompositePk", "formData", "formDate", "formId", "formName", "formProvider", "formTime", "status", "subject" })
public class CachedEformData extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer caisiDemographicId;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String formData;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar formDate;
    protected Integer formId;
    protected String formName;
    protected String formProvider;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar formTime;
    protected Boolean status;
    protected String subject;
    
    public Integer getCaisiDemographicId() {
        return this.caisiDemographicId;
    }
    
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }
    
    public String getFormData() {
        return this.formData;
    }
    
    public void setFormData(final String formData) {
        this.formData = formData;
    }
    
    public Calendar getFormDate() {
        return this.formDate;
    }
    
    public void setFormDate(final Calendar formDate) {
        this.formDate = formDate;
    }
    
    public Integer getFormId() {
        return this.formId;
    }
    
    public void setFormId(final Integer formId) {
        this.formId = formId;
    }
    
    public String getFormName() {
        return this.formName;
    }
    
    public void setFormName(final String formName) {
        this.formName = formName;
    }
    
    public String getFormProvider() {
        return this.formProvider;
    }
    
    public void setFormProvider(final String formProvider) {
        this.formProvider = formProvider;
    }
    
    public Calendar getFormTime() {
        return this.formTime;
    }
    
    public void setFormTime(final Calendar formTime) {
        this.formTime = formTime;
    }
    
    public Boolean isStatus() {
        return this.status;
    }
    
    public void setStatus(final Boolean status) {
        this.status = status;
    }
    
    public String getSubject() {
        return this.subject;
    }
    
    public void setSubject(final String subject) {
        this.subject = subject;
    }
}
