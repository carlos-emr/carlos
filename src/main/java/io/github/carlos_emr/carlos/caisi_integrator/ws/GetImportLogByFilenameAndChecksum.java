package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getImportLogByFilenameAndChecksum", propOrder = { "arg0", "arg1" })
public class GetImportLogByFilenameAndChecksum implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected String arg0;
    protected String arg1;
    
    public String getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final String arg0) {
        this.arg0 = arg0;
    }
    
    public String getArg1() {
        return this.arg1;
    }
    
    public void setArg1(final String arg1) {
        this.arg1 = arg1;
    }
}
