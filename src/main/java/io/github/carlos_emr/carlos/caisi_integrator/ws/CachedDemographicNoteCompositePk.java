package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedDemographicNoteCompositePk", propOrder = { "integratorFacilityId", "uuid" })
public class CachedDemographicNoteCompositePk implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer integratorFacilityId;
    protected String uuid;
    
    public Integer getIntegratorFacilityId() {
        return this.integratorFacilityId;
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        this.integratorFacilityId = integratorFacilityId;
    }
    
    public String getUuid() {
        return this.uuid;
    }
    
    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }
}
