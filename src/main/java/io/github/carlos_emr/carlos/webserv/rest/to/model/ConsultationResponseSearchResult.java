/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;

@jakarta.xml.bind.annotation.XmlType(propOrder={"appointmentDate", "demographicSnapshot", "id", "lastFollowUp", "providerSnapshot", "referralDate", "referringDoctorSnapshot", "responseDate", "status", "teamName", "urgency"})
public class ConsultationResponseSearchResult {

    private Integer id;
    private EmbeddedDemographicTo1 demographic;
    private EmbeddedProfessionalSpecialistTo1 referringDoctor;
    private String teamName;
    private String status;
    private EmbeddedProviderTo1 provider;
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date appointmentDate;
    private Date lastFollowUp;
    private Date referralDate;
    private Date responseDate;
    private String urgency;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /** Detached Java compatibility view; the wire uses the dedicated snapshot below. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.xml.bind.annotation.XmlTransient
    public Demographic getDemographic() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.demographicDomain(demographic); }

    @com.fasterxml.jackson.annotation.JsonProperty("demographic")
    @jakarta.xml.bind.annotation.XmlElement(name="demographic")
    public EmbeddedDemographicTo1 getDemographicSnapshot() { return demographic; }
    public void setDemographicSnapshot(EmbeddedDemographicTo1 value) { demographic = value; }

    public void setDemographic(Demographic demographic) {
        this.demographic = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.demographic(null, demographic);
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(Date appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public Date getLastFollowUp() {
        return lastFollowUp;
    }

    public void setLastFollowUp(Date lastFollowUp) {
        this.lastFollowUp = lastFollowUp;
    }

    public Date getReferralDate() {
        return referralDate;
    }

    public void setReferralDate(Date referralDate) {
        this.referralDate = referralDate;
    }

    public Date getResponseDate() {
        return responseDate;
    }

    public void setResponseDate(Date responseDate) {
        this.responseDate = responseDate;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    /** Detached Java compatibility view; the wire uses the dedicated snapshot below. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.xml.bind.annotation.XmlTransient
    public ProfessionalSpecialist getReferringDoctor() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.professionalSpecialistDomain(referringDoctor); }

    @com.fasterxml.jackson.annotation.JsonProperty("referringDoctor")
    @jakarta.xml.bind.annotation.XmlElement(name="referringDoctor")
    public EmbeddedProfessionalSpecialistTo1 getReferringDoctorSnapshot() { return referringDoctor; }
    public void setReferringDoctorSnapshot(EmbeddedProfessionalSpecialistTo1 value) { referringDoctor = value; }

    public void setReferringDoctor(ProfessionalSpecialist referringDoctor) {
        this.referringDoctor = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.professionalSpecialist(null, referringDoctor);
    }

    /** Detached Java compatibility view; the wire uses the dedicated snapshot below. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.xml.bind.annotation.XmlTransient
    public Provider getProvider() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.providerDomain(provider); }

    @com.fasterxml.jackson.annotation.JsonProperty("provider")
    @jakarta.xml.bind.annotation.XmlElement(name="provider")
    public EmbeddedProviderTo1 getProviderSnapshot() { return provider; }
    public void setProviderSnapshot(EmbeddedProviderTo1 value) { provider = value; }

    public void setProvider(Provider provider) {
        this.provider = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.provider(null, provider);
    }
}
