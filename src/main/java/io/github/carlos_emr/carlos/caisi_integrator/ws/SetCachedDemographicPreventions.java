package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "setCachedDemographicPreventions", propOrder = { "arg0" })
public class SetCachedDemographicPreventions implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected List<CachedDemographicPrevention> arg0;
    
    public List<CachedDemographicPrevention> getArg0() {
        if (this.arg0 == null) {
            this.arg0 = new ArrayList<CachedDemographicPrevention>();
        }
        return this.arg0;
    }
}
