package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "facilityIdStringCompositePk", propOrder = { "caisiItemId", "integratorFacilityId" })
public class FacilityIdStringCompositePk implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected String caisiItemId;
    protected Integer integratorFacilityId;
    
    public String getCaisiItemId() {
        return this.caisiItemId;
    }
    
    public void setCaisiItemId(final String caisiItemId) {
        this.caisiItemId = caisiItemId;
    }
    
    public Integer getIntegratorFacilityId() {
        return this.integratorFacilityId;
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        this.integratorFacilityId = integratorFacilityId;
    }
}
