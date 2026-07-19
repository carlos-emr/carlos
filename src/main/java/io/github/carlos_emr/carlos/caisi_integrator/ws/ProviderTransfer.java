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
@XmlType(name = "providerTransfer", propOrder = { "cachedProvider", "roles" })
public class ProviderTransfer implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected CachedProvider cachedProvider;
    @XmlElement(nillable = true)
    @XmlSchemaType(name = "string")
    protected List<Role> roles;
    
    public CachedProvider getCachedProvider() {
        return this.cachedProvider;
    }
    
    public void setCachedProvider(final CachedProvider cachedProvider) {
        this.cachedProvider = cachedProvider;
    }
    
    public List<Role> getRoles() {
        if (this.roles == null) {
            this.roles = new ArrayList<Role>();
        }
        return this.roles;
    }
}
