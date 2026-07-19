package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "addCachedDemographicDocumentContents", propOrder = { "arg0", "arg1" })
public class AddCachedDemographicDocumentContents implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected int arg0;
    protected byte[] arg1;
    
    public int getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final int arg0) {
        this.arg0 = arg0;
    }
    
    public byte[] getArg1() {
        return this.arg1;
    }
    
    public void setArg1(final byte[] arg1) {
        this.arg1 = arg1;
    }
}
