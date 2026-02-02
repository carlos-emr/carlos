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
@XmlType(name = "cachedDemographicForm", propOrder = { "caisiDemographicId", "caisiProviderId", "editDate", "facilityIdIntegerCompositePk", "formData", "formName" })
public class CachedDemographicForm extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer caisiDemographicId;
    protected String caisiProviderId;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar editDate;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String formData;
    protected String formName;
    
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
    
    public Calendar getEditDate() {
        return this.editDate;
    }
    
    public void setEditDate(final Calendar editDate) {
        this.editDate = editDate;
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
    
    public String getFormName() {
        return this.formName;
    }
    
    public void setFormName(final String formName) {
        this.formName = formName;
    }
}
