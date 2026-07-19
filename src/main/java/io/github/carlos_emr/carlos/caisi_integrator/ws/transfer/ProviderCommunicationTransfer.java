package io.github.carlos_emr.carlos.caisi_integrator.ws.transfer;

import java.util.Date;
import java.io.Serializable;

public class ProviderCommunicationTransfer implements Serializable
{
    private static final long serialVersionUID = -2332340755291901610L;
    private Integer id;
    private Integer sourceIntegratorFacilityId;
    private String sourceProviderId;
    private Integer destinationIntegratorFacilityId;
    private String destinationProviderId;
    private Date sentDate;
    private boolean active;
    private String type;
    private byte[] data;
    
    public ProviderCommunicationTransfer() {
        this.id = null;
        this.sourceIntegratorFacilityId = null;
        this.sourceProviderId = null;
        this.destinationIntegratorFacilityId = null;
        this.destinationProviderId = null;
        this.sentDate = new Date();
        this.active = true;
        this.type = null;
        this.data = null;
    }
    
    public Integer getId() {
        return this.id;
    }
    
    public void setId(final Integer id) {
        this.id = id;
    }
    
    public Integer getSourceIntegratorFacilityId() {
        return this.sourceIntegratorFacilityId;
    }
    
    public void setSourceIntegratorFacilityId(final Integer sourceIntegratorFacilityId) {
        this.sourceIntegratorFacilityId = sourceIntegratorFacilityId;
    }
    
    public String getSourceProviderId() {
        return this.sourceProviderId;
    }
    
    public void setSourceProviderId(final String sourceProviderId) {
        this.sourceProviderId = sourceProviderId;
    }
    
    public Integer getDestinationIntegratorFacilityId() {
        return this.destinationIntegratorFacilityId;
    }
    
    public void setDestinationIntegratorFacilityId(final Integer destinationIntegratorFacilityId) {
        this.destinationIntegratorFacilityId = destinationIntegratorFacilityId;
    }
    
    public String getDestinationProviderId() {
        return this.destinationProviderId;
    }
    
    public void setDestinationProviderId(final String destinationProviderId) {
        this.destinationProviderId = destinationProviderId;
    }
    
    public Date getSentDate() {
        return this.sentDate;
    }
    
    public void setSentDate(final Date sentDate) {
        this.sentDate = sentDate;
    }
    
    public boolean isActive() {
        return this.active;
    }
    
    public void setActive(final boolean active) {
        this.active = active;
    }
    
    public String getType() {
        return this.type;
    }
    
    public void setType(final String type) {
        this.type = type;
    }
    
    public byte[] getData() {
        return this.data;
    }
    
    public void setData(final byte[] data) {
        this.data = data;
    }
}
