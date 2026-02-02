package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "matchingDemographicTransferScore", propOrder = { "demographicTransfer", "score" })
public class MatchingDemographicTransferScore implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected DemographicTransfer demographicTransfer;
    protected int score;
    
    public DemographicTransfer getDemographicTransfer() {
        return this.demographicTransfer;
    }
    
    public void setDemographicTransfer(final DemographicTransfer demographicTransfer) {
        this.demographicTransfer = demographicTransfer;
    }
    
    public int getScore() {
        return this.score;
    }
    
    public void setScore(final int score) {
        this.score = score;
    }
}
