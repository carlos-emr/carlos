package io.github.carlos_emr.carlos.caisi_integrator.ws.transfer;

import io.github.carlos_emr.carlos.caisi_integrator.util.Role;
import java.util.ArrayList;
import io.github.carlos_emr.carlos.caisi_integrator.dao.CachedProvider;
import java.io.Serializable;

public class ProviderTransfer implements Serializable
{
    private static final long serialVersionUID = 8127876095215434516L;
    private CachedProvider cachedProvider;
    private ArrayList<Role> roles;
    
    public ProviderTransfer() {
        this.cachedProvider = null;
        this.roles = null;
    }
    
    public CachedProvider getCachedProvider() {
        return this.cachedProvider;
    }
    
    public void setCachedProvider(final CachedProvider cachedProvider) {
        this.cachedProvider = cachedProvider;
    }
    
    public ArrayList<Role> getRoles() {
        return this.roles;
    }
    
    public void setRoles(final ArrayList<Role> roles) {
        this.roles = roles;
    }
}
