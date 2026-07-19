package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "deleteCachedProgramsMissingFromList", propOrder = { "arg0" })
public class DeleteCachedProgramsMissingFromList implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(type = Integer.class)
    protected List<Integer> arg0;
    
    public List<Integer> getArg0() {
        if (this.arg0 == null) {
            this.arg0 = new ArrayList<Integer>();
        }
        return this.arg0;
    }
}
