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
@XmlType(name = "importLog", propOrder = { "checksum", "dateCreated", "dateIntervalEnd", "dateIntervalStart", "dependsOn", "facilityId", "filename", "id", "lastUpdatedDate", "status" })
public class ImportLog extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected String checksum;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar dateCreated;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar dateIntervalEnd;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar dateIntervalStart;
    protected String dependsOn;
    protected Integer facilityId;
    protected String filename;
    protected Long id;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar lastUpdatedDate;
    protected String status;
    
    public String getChecksum() {
        return this.checksum;
    }
    
    public void setChecksum(final String checksum) {
        this.checksum = checksum;
    }
    
    public Calendar getDateCreated() {
        return this.dateCreated;
    }
    
    public void setDateCreated(final Calendar dateCreated) {
        this.dateCreated = dateCreated;
    }
    
    public Calendar getDateIntervalEnd() {
        return this.dateIntervalEnd;
    }
    
    public void setDateIntervalEnd(final Calendar dateIntervalEnd) {
        this.dateIntervalEnd = dateIntervalEnd;
    }
    
    public Calendar getDateIntervalStart() {
        return this.dateIntervalStart;
    }
    
    public void setDateIntervalStart(final Calendar dateIntervalStart) {
        this.dateIntervalStart = dateIntervalStart;
    }
    
    public String getDependsOn() {
        return this.dependsOn;
    }
    
    public void setDependsOn(final String dependsOn) {
        this.dependsOn = dependsOn;
    }
    
    public Integer getFacilityId() {
        return this.facilityId;
    }
    
    public void setFacilityId(final Integer facilityId) {
        this.facilityId = facilityId;
    }
    
    public String getFilename() {
        return this.filename;
    }
    
    public void setFilename(final String filename) {
        this.filename = filename;
    }
    
    public Long getId() {
        return this.id;
    }
    
    public void setId(final Long id) {
        this.id = id;
    }
    
    public Calendar getLastUpdatedDate() {
        return this.lastUpdatedDate;
    }
    
    public void setLastUpdatedDate(final Calendar lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }
    
    public String getStatus() {
        return this.status;
    }
    
    public void setStatus(final String status) {
        this.status = status;
    }
}
