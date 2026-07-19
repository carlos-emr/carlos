package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import org.w3._2001.xmlschema.Adapter1;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.XmlElement;
import java.util.Calendar;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "createImportLogWithStatus", propOrder = { "arg0", "arg1", "arg2", "arg3", "arg4", "arg5" })
public class CreateImportLogWithStatus implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected String arg0;
    protected String arg1;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar arg2;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar arg3;
    protected String arg4;
    protected String arg5;
    
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
    
    public Calendar getArg2() {
        return this.arg2;
    }
    
    public void setArg2(final Calendar arg2) {
        this.arg2 = arg2;
    }
    
    public Calendar getArg3() {
        return this.arg3;
    }
    
    public void setArg3(final Calendar arg3) {
        this.arg3 = arg3;
    }
    
    public String getArg4() {
        return this.arg4;
    }
    
    public void setArg4(final String arg4) {
        this.arg4 = arg4;
    }
    
    public String getArg5() {
        return this.arg5;
    }
    
    public void setArg5(final String arg5) {
        this.arg5 = arg5;
    }
}
