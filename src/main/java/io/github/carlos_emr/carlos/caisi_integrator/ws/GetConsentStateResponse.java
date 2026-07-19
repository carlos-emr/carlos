package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getConsentStateResponse", propOrder = { "_return" })
public class GetConsentStateResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected GetConsentTransfer _return;
    
    public GetConsentTransfer getReturn() {
        return this._return;
    }
    
    public void setReturn(final GetConsentTransfer return1) {
        this._return = return1;
    }
}
