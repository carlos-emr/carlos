package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "addCachedDemographicLabResult", propOrder = { "arg0" })
public class AddCachedDemographicLabResult implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected CachedDemographicLabResult arg0;
    
    public CachedDemographicLabResult getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final CachedDemographicLabResult arg0) {
        this.arg0 = arg0;
    }
}
