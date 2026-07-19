package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "setCachedDemographicAllergies", propOrder = { "arg0" })
public class SetCachedDemographicAllergies implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected List<CachedDemographicAllergy> arg0;
    
    public List<CachedDemographicAllergy> getArg0() {
        if (this.arg0 == null) {
            this.arg0 = new ArrayList<CachedDemographicAllergy>();
        }
        return this.arg0;
    }
}
