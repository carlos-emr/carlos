package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getLinkedCachedDemographicAllergiesResponse", propOrder = { "_return" })
public class GetLinkedCachedDemographicAllergiesResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected List<CachedDemographicAllergy> _return;
    
    public List<CachedDemographicAllergy> getReturn() {
        if (this._return == null) {
            this._return = new ArrayList<CachedDemographicAllergy>();
        }
        return this._return;
    }
}
