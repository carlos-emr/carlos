package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "makeReferral", propOrder = { "arg0" })
public class MakeReferral implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Referral arg0;
    
    public Referral getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final Referral arg0) {
        this.arg0 = arg0;
    }
}
