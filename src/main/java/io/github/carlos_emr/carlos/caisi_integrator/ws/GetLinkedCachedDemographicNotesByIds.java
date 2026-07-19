package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getLinkedCachedDemographicNotesByIds", propOrder = { "cachedDemographicNoteCompositePk" })
public class GetLinkedCachedDemographicNotesByIds implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected List<CachedDemographicNoteCompositePk> cachedDemographicNoteCompositePk;
    
    public List<CachedDemographicNoteCompositePk> getCachedDemographicNoteCompositePk() {
        if (this.cachedDemographicNoteCompositePk == null) {
            this.cachedDemographicNoteCompositePk = new ArrayList<CachedDemographicNoteCompositePk>();
        }
        return this.cachedDemographicNoteCompositePk;
    }
}
