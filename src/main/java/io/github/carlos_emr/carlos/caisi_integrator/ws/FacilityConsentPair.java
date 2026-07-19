package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "facilityConsentPair", propOrder = { "remoteFacilityId", "shareData" })
public class FacilityConsentPair implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer remoteFacilityId;
    protected boolean shareData;
    
    public Integer getRemoteFacilityId() {
        return this.remoteFacilityId;
    }
    
    public void setRemoteFacilityId(final Integer remoteFacilityId) {
        this.remoteFacilityId = remoteFacilityId;
    }
    
    public boolean isShareData() {
        return this.shareData;
    }
    
    public void setShareData(final boolean shareData) {
        this.shareData = shareData;
    }
}
