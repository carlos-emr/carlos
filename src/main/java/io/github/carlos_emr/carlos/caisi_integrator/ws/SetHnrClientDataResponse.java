package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "setHnrClientDataResponse", propOrder = { "_return" })
public class SetHnrClientDataResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    protected Integer _return;
    
    public Integer getReturn() {
        return this._return;
    }
    
    public void setReturn(final Integer return1) {
        this._return = return1;
    }
}
