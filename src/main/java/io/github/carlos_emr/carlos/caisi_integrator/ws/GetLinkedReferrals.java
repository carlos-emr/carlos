package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getLinkedReferrals", propOrder = { "sourceCaisiDemographicId" })
public class GetLinkedReferrals implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer sourceCaisiDemographicId;
    
    public Integer getSourceCaisiDemographicId() {
        return this.sourceCaisiDemographicId;
    }
    
    public void setSourceCaisiDemographicId(final Integer sourceCaisiDemographicId) {
        this.sourceCaisiDemographicId = sourceCaisiDemographicId;
    }
}
