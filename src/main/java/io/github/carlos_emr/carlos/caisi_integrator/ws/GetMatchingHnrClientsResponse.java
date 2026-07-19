package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElement;
import io.github.carlos_emr.carlos.ws.MatchingClientScore;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getMatchingHnrClientsResponse", propOrder = { "_return" })
public class GetMatchingHnrClientsResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected List<MatchingClientScore> _return;
    
    public List<MatchingClientScore> getReturn() {
        if (this._return == null) {
            this._return = new ArrayList<MatchingClientScore>();
        }
        return this._return;
    }
}
