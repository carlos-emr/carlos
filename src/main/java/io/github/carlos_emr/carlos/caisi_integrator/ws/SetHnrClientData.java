package io.github.carlos_emr.carlos.caisi_integrator.ws;

import io.github.carlos_emr.carlos.ws.Client;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "setHnrClientData", propOrder = { "arg0" })
public class SetHnrClientData implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Client arg0;
    
    public Client getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final Client arg0) {
        this.arg0 = arg0;
    }
}
