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
@XmlType(name = "providerCommunicationTransfer", propOrder = { "active", "data", "destinationIntegratorFacilityId", "destinationProviderId", "id", "sentDate", "sourceIntegratorFacilityId", "sourceProviderId", "type" })
public class ProviderCommunicationTransfer implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected boolean active;
    protected byte[] data;
    protected Integer destinationIntegratorFacilityId;
    protected String destinationProviderId;
    protected Integer id;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar sentDate;
    protected Integer sourceIntegratorFacilityId;
    protected String sourceProviderId;
    protected String type;
    
    public boolean isActive() {
        return this.active;
    }
    
    public void setActive(final boolean active) {
        this.active = active;
    }
    
    public byte[] getData() {
        return this.data;
    }
    
    public void setData(final byte[] data) {
        this.data = data;
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
    
    public Integer getId() {
        return this.id;
    }
    
    public void setId(final Integer id) {
        this.id = id;
    }
    
    public Calendar getSentDate() {
        return this.sentDate;
    }
    
    public void setSentDate(final Calendar sentDate) {
        this.sentDate = sentDate;
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
    
    public String getType() {
        return this.type;
    }
    
    public void setType(final String type) {
        this.type = type;
    }
}
