package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "setCachedDemographicConsent", propOrder = { "consentTransfer" })
public class SetCachedDemographicConsent implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected SetConsentTransfer consentTransfer;
    
    public SetConsentTransfer getConsentTransfer() {
        return this.consentTransfer;
    }
    
    public void setConsentTransfer(final SetConsentTransfer consentTransfer) {
        this.consentTransfer = consentTransfer;
    }
}
