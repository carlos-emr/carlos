package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "facilityIdDemographicIssueCompositePk", propOrder = { "caisiDemographicId", "codeType", "integratorFacilityId", "issueCode" })
public class FacilityIdDemographicIssueCompositePk implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer caisiDemographicId;
    @XmlSchemaType(name = "string")
    protected CodeType codeType;
    protected Integer integratorFacilityId;
    protected String issueCode;
    
    public Integer getCaisiDemographicId() {
        return this.caisiDemographicId;
    }
    
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }
    
    public CodeType getCodeType() {
        return this.codeType;
    }
    
    public void setCodeType(final CodeType codeType) {
        this.codeType = codeType;
    }
    
    public Integer getIntegratorFacilityId() {
        return this.integratorFacilityId;
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        this.integratorFacilityId = integratorFacilityId;
    }
    
    public String getIssueCode() {
        return this.issueCode;
    }
    
    public void setIssueCode(final String issueCode) {
        this.issueCode = issueCode;
    }
}
