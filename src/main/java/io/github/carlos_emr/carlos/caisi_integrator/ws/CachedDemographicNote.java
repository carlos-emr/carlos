package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlSchemaType;
import org.w3._2001.xmlschema.Adapter1;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Calendar;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedDemographicNote", propOrder = { "cachedDemographicNoteCompositePk", "caisiDemographicId", "caisiProgramId", "encounterType", "issues", "note", "observationCaisiProviderId", "observationDate", "role", "signingCaisiProviderId", "updateDate" })
public class CachedDemographicNote extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk;
    protected int caisiDemographicId;
    protected int caisiProgramId;
    protected String encounterType;
    @XmlElement(nillable = true)
    protected List<NoteIssue> issues;
    protected String note;
    protected String observationCaisiProviderId;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar observationDate;
    protected String role;
    protected String signingCaisiProviderId;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar updateDate;
    
    public CachedDemographicNoteCompositePk getCachedDemographicNoteCompositePk() {
        return this.cachedDemographicNoteCompositePk;
    }
    
    public void setCachedDemographicNoteCompositePk(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk) {
        this.cachedDemographicNoteCompositePk = cachedDemographicNoteCompositePk;
    }
    
    public int getCaisiDemographicId() {
        return this.caisiDemographicId;
    }
    
    public void setCaisiDemographicId(final int caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }
    
    public int getCaisiProgramId() {
        return this.caisiProgramId;
    }
    
    public void setCaisiProgramId(final int caisiProgramId) {
        this.caisiProgramId = caisiProgramId;
    }
    
    public String getEncounterType() {
        return this.encounterType;
    }
    
    public void setEncounterType(final String encounterType) {
        this.encounterType = encounterType;
    }
    
    public List<NoteIssue> getIssues() {
        if (this.issues == null) {
            this.issues = new ArrayList<NoteIssue>();
        }
        return this.issues;
    }
    
    public String getNote() {
        return this.note;
    }
    
    public void setNote(final String note) {
        this.note = note;
    }
    
    public String getObservationCaisiProviderId() {
        return this.observationCaisiProviderId;
    }
    
    public void setObservationCaisiProviderId(final String observationCaisiProviderId) {
        this.observationCaisiProviderId = observationCaisiProviderId;
    }
    
    public Calendar getObservationDate() {
        return this.observationDate;
    }
    
    public void setObservationDate(final Calendar observationDate) {
        this.observationDate = observationDate;
    }
    
    public String getRole() {
        return this.role;
    }
    
    public void setRole(final String role) {
        this.role = role;
    }
    
    public String getSigningCaisiProviderId() {
        return this.signingCaisiProviderId;
    }
    
    public void setSigningCaisiProviderId(final String signingCaisiProviderId) {
        this.signingCaisiProviderId = signingCaisiProviderId;
    }
    
    public Calendar getUpdateDate() {
        return this.updateDate;
    }
    
    public void setUpdateDate(final Calendar updateDate) {
        this.updateDate = updateDate;
    }
}
