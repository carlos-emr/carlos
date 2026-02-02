package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getConsentState", propOrder = { "integratorFacilityAndDemographicId" })
public class GetConsentState implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdIntegerCompositePk integratorFacilityAndDemographicId;
    
    public FacilityIdIntegerCompositePk getIntegratorFacilityAndDemographicId() {
        return this.integratorFacilityAndDemographicId;
    }
    
    public void setIntegratorFacilityAndDemographicId(final FacilityIdIntegerCompositePk integratorFacilityAndDemographicId) {
        this.integratorFacilityAndDemographicId = integratorFacilityAndDemographicId;
    }
}
