package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getCachedDemographicPreventionsByPreventionId", propOrder = { "preventionId" })
public class GetCachedDemographicPreventionsByPreventionId implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdIntegerCompositePk preventionId;
    
    public FacilityIdIntegerCompositePk getPreventionId() {
        return this.preventionId;
    }
    
    public void setPreventionId(final FacilityIdIntegerCompositePk preventionId) {
        this.preventionId = preventionId;
    }
}
