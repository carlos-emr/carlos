package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getReferral", propOrder = { "referralId" })
public class GetReferral implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer referralId;
    
    public Integer getReferralId() {
        return this.referralId;
    }
    
    public void setReferralId(final Integer referralId) {
        this.referralId = referralId;
    }
}
