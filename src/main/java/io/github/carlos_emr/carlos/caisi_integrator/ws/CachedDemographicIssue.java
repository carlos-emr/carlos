package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedDemographicIssue", propOrder = { "acute", "certain", "facilityDemographicIssuePk", "issueDescription", "issueRole", "major", "resolved" })
public class CachedDemographicIssue extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Boolean acute;
    protected Boolean certain;
    protected FacilityIdDemographicIssueCompositePk facilityDemographicIssuePk;
    protected String issueDescription;
    @XmlSchemaType(name = "string")
    protected Role issueRole;
    protected Boolean major;
    protected Boolean resolved;
    
    public Boolean isAcute() {
        return this.acute;
    }
    
    public void setAcute(final Boolean acute) {
        this.acute = acute;
    }
    
    public Boolean isCertain() {
        return this.certain;
    }
    
    public void setCertain(final Boolean certain) {
        this.certain = certain;
    }
    
    public FacilityIdDemographicIssueCompositePk getFacilityDemographicIssuePk() {
        return this.facilityDemographicIssuePk;
    }
    
    public void setFacilityDemographicIssuePk(final FacilityIdDemographicIssueCompositePk facilityDemographicIssuePk) {
        this.facilityDemographicIssuePk = facilityDemographicIssuePk;
    }
    
    public String getIssueDescription() {
        return this.issueDescription;
    }
    
    public void setIssueDescription(final String issueDescription) {
        this.issueDescription = issueDescription;
    }
    
    public Role getIssueRole() {
        return this.issueRole;
    }
    
    public void setIssueRole(final Role issueRole) {
        this.issueRole = issueRole;
    }
    
    public Boolean isMajor() {
        return this.major;
    }
    
    public void setMajor(final Boolean major) {
        this.major = major;
    }
    
    public Boolean isResolved() {
        return this.resolved;
    }
    
    public void setResolved(final Boolean resolved) {
        this.resolved = resolved;
    }
}
