package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlMimeType;
import javax.activation.DataHandler;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedDemographicDocumentContents", propOrder = { "facilityIntegerCompositePk", "fileContents" })
public class CachedDemographicDocumentContents extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdIntegerCompositePk facilityIntegerCompositePk;
    @XmlMimeType("application/octet-stream")
    protected DataHandler fileContents;
    
    public FacilityIdIntegerCompositePk getFacilityIntegerCompositePk() {
        return this.facilityIntegerCompositePk;
    }
    
    public void setFacilityIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIntegerCompositePk) {
        this.facilityIntegerCompositePk = facilityIntegerCompositePk;
    }
    
    public DataHandler getFileContents() {
        return this.fileContents;
    }
    
    public void setFileContents(final DataHandler fileContents) {
        this.fileContents = fileContents;
    }
}
