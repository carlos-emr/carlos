package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedEformValue", propOrder = { "caisiDemographicId", "facilityIdIntegerCompositePk", "formDataId", "formId", "varName", "varValue" })
public class CachedEformValue extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer caisiDemographicId;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected Integer formDataId;
    protected Integer formId;
    protected String varName;
    protected String varValue;
    
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
    
    public Integer getFormDataId() {
        return this.formDataId;
    }
    
    public void setFormDataId(final Integer formDataId) {
        this.formDataId = formDataId;
    }
    
    public Integer getFormId() {
        return this.formId;
    }
    
    public void setFormId(final Integer formId) {
        this.formId = formId;
    }
    
    public String getVarName() {
        return this.varName;
    }
    
    public void setVarName(final String varName) {
        this.varName = varName;
    }
    
    public String getVarValue() {
        return this.varValue;
    }
    
    public void setVarValue(final String varValue) {
        this.varValue = varValue;
    }
}
