package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getProviderRolesResponse", propOrder = { "_return" })
public class GetProviderRolesResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return")
    @XmlSchemaType(name = "string")
    protected List<Role> _return;
    
    public List<Role> getReturn() {
        if (this._return == null) {
            this._return = new ArrayList<Role>();
        }
        return this._return;
    }
}
