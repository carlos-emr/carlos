package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "addCachedDemographicDocumentAndContents", propOrder = { "arg0", "arg1" })
public class AddCachedDemographicDocumentAndContents implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected CachedDemographicDocument arg0;
    protected byte[] arg1;
    
    public CachedDemographicDocument getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final CachedDemographicDocument arg0) {
        this.arg0 = arg0;
    }
    
    public byte[] getArg1() {
        return this.arg1;
    }
    
    public void setArg1(final byte[] arg1) {
        this.arg1 = arg1;
    }
}
