package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getCachedDemographicLabResult", propOrder = { "arg0" })
public class GetCachedDemographicLabResult implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdLabResultCompositePk arg0;
    
    public FacilityIdLabResultCompositePk getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final FacilityIdLabResultCompositePk arg0) {
        this.arg0 = arg0;
    }
}
