package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "addCachedDemographicDocument", propOrder = { "arg0" })
public class AddCachedDemographicDocument implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected CachedDemographicDocument arg0;
    
    public CachedDemographicDocument getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final CachedDemographicDocument arg0) {
        this.arg0 = arg0;
    }
}
