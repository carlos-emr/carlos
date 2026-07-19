package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "facilityIdLabResultCompositePk", propOrder = { "integratorFacilityId", "labResultId" })
public class FacilityIdLabResultCompositePk implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer integratorFacilityId;
    protected String labResultId;
    
    public Integer getIntegratorFacilityId() {
        return this.integratorFacilityId;
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        this.integratorFacilityId = integratorFacilityId;
    }
    
    public String getLabResultId() {
        return this.labResultId;
    }
    
    public void setLabResultId(final String labResultId) {
        this.labResultId = labResultId;
    }
}
