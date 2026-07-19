package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "addCachedDemographicHL7LabResult", propOrder = { "arg0" })
public class AddCachedDemographicHL7LabResult implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected CachedDemographicHL7LabResult arg0;
    
    public CachedDemographicHL7LabResult getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final CachedDemographicHL7LabResult arg0) {
        this.arg0 = arg0;
    }
}
