package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getHnrClient", propOrder = { "linkingId" })
public class GetHnrClient implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer linkingId;
    
    public Integer getLinkingId() {
        return this.linkingId;
    }
    
    public void setLinkingId(final Integer linkingId) {
        this.linkingId = linkingId;
    }
}
