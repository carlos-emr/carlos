package io.github.carlos_emr.carlos.caisi_integrator.ws;

import io.github.carlos_emr.carlos.ws.MatchingClientParameters;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getMatchingHnrClients", propOrder = { "arg0" })
public class GetMatchingHnrClients implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected MatchingClientParameters arg0;
    
    public MatchingClientParameters getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final MatchingClientParameters arg0) {
        this.arg0 = arg0;
    }
}
