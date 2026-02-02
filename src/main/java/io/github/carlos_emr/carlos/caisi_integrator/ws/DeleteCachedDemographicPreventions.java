package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "deleteCachedDemographicPreventions", propOrder = { "arg0", "arg1" })
public class DeleteCachedDemographicPreventions implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer arg0;
    @XmlElement(type = Integer.class)
    protected List<Integer> arg1;
    
    public Integer getArg0() {
        return this.arg0;
    }
    
    public void setArg0(final Integer arg0) {
        this.arg0 = arg0;
    }
    
    public List<Integer> getArg1() {
        if (this.arg1 == null) {
            this.arg1 = new ArrayList<Integer>();
        }
        return this.arg1;
    }
}
