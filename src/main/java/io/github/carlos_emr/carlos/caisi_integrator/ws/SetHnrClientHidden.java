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
@XmlType(name = "setHnrClientHidden", propOrder = { "arg0", "arg1", "arg2" })
public class SetHnrClientHidden implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer arg0;
    protected boolean arg1;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar arg2;
    
    public Integer getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final Integer arg0) {
        this.arg0 = arg0;
    }
    
    public boolean isArg1() {
        return this.arg1;
    }
    
    public void setArg1(final boolean arg1) {
        this.arg1 = arg1;
    }
    
    public Calendar getArg2() {
        return this.arg2;
    }
    
    public void setArg2(final Calendar arg2) {
        this.arg2 = arg2;
    }
}
