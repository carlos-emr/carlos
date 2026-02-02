package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "setMyFacility", propOrder = { "arg0" })
public class SetMyFacility implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected CachedFacility arg0;
    
    public CachedFacility getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final CachedFacility arg0) {
        this.arg0 = arg0;
    }
}
