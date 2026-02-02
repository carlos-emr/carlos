package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getMatchingDemographicsResponse", propOrder = { "_return" })
public class GetMatchingDemographicsResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected List<MatchingDemographicTransferScore> _return;
    
    public List<MatchingDemographicTransferScore> getReturn() {
        if (this._return == null) {
            this._return = new ArrayList<MatchingDemographicTransferScore>();
        }
        return this._return;
    }
}
