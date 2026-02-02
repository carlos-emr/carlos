package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getCachedDemographicDocumentContents", propOrder = { "arg0" })
public class GetCachedDemographicDocumentContents implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdIntegerCompositePk arg0;
    
    public FacilityIdIntegerCompositePk getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final FacilityIdIntegerCompositePk arg0) {
        this.arg0 = arg0;
    }
}
