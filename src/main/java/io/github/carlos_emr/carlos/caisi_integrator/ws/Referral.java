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
@XmlType(name = "referral", propOrder = { "destinationCaisiProgramId", "destinationIntegratorFacilityId", "presentingProblem", "reasonForReferral", "referralDate", "referralId", "sourceCaisiDemographicId", "sourceCaisiProviderId", "sourceIntegratorFacilityId" })
public class Referral extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Integer destinationCaisiProgramId;
    protected Integer destinationIntegratorFacilityId;
    protected String presentingProblem;
    protected String reasonForReferral;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar referralDate;
    protected Integer referralId;
    protected Integer sourceCaisiDemographicId;
    protected String sourceCaisiProviderId;
    protected Integer sourceIntegratorFacilityId;
    
    public Integer getDestinationCaisiProgramId() {
        return this.destinationCaisiProgramId;
    }
    
    public void setDestinationCaisiProgramId(final Integer destinationCaisiProgramId) {
        this.destinationCaisiProgramId = destinationCaisiProgramId;
    }
    
    public Integer getDestinationIntegratorFacilityId() {
        return this.destinationIntegratorFacilityId;
    }
    
    public void setDestinationIntegratorFacilityId(final Integer destinationIntegratorFacilityId) {
        this.destinationIntegratorFacilityId = destinationIntegratorFacilityId;
    }
    
    public String getPresentingProblem() {
        return this.presentingProblem;
    }
    
    public void setPresentingProblem(final String presentingProblem) {
        this.presentingProblem = presentingProblem;
    }
    
    public String getReasonForReferral() {
        return this.reasonForReferral;
    }
    
    public void setReasonForReferral(final String reasonForReferral) {
        this.reasonForReferral = reasonForReferral;
    }
    
    public Calendar getReferralDate() {
        return this.referralDate;
    }
    
    public void setReferralDate(final Calendar referralDate) {
        this.referralDate = referralDate;
    }
    
    public Integer getReferralId() {
        return this.referralId;
    }
    
    public void setReferralId(final Integer referralId) {
        this.referralId = referralId;
    }
    
    public Integer getSourceCaisiDemographicId() {
        return this.sourceCaisiDemographicId;
    }
    
    public void setSourceCaisiDemographicId(final Integer sourceCaisiDemographicId) {
        this.sourceCaisiDemographicId = sourceCaisiDemographicId;
    }
    
    public String getSourceCaisiProviderId() {
        return this.sourceCaisiProviderId;
    }
    
    public void setSourceCaisiProviderId(final String sourceCaisiProviderId) {
        this.sourceCaisiProviderId = sourceCaisiProviderId;
    }
    
    public Integer getSourceIntegratorFacilityId() {
        return this.sourceIntegratorFacilityId;
    }
    
    public void setSourceIntegratorFacilityId(final Integer sourceIntegratorFacilityId) {
        this.sourceIntegratorFacilityId = sourceIntegratorFacilityId;
    }
}
