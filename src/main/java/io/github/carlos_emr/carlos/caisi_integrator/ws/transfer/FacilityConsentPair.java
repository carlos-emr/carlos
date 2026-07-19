package io.github.carlos_emr.carlos.caisi_integrator.ws.transfer;

public class FacilityConsentPair
{
    private Integer remoteFacilityId;
    private boolean shareData;
    
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
