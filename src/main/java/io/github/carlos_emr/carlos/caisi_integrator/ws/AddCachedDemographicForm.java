package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "addCachedDemographicForm", propOrder = { "arg0" })
public class AddCachedDemographicForm implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected CachedDemographicForm arg0;
    
    public CachedDemographicForm getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final CachedDemographicForm arg0) {
        this.arg0 = arg0;
    }
}
