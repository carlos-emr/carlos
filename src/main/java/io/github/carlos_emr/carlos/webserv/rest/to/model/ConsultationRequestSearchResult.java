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

@jakarta.xml.bind.annotation.XmlType(propOrder={"appointmentDate", "consultantSnapshot", "demographicSnapshot", "id", "lastFollowUp", "mrpSnapshot", "referralDate", "serviceName", "status", "teamName", "urgency"})
public class ConsultationRequestSearchResult {

    private Integer id;
    private EmbeddedDemographicTo1 demographic;
    private String serviceName;
    private EmbeddedProfessionalSpecialistTo1 consultant;
    private String teamName;
    private String status;
    private EmbeddedProviderTo1 mrp;
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date appointmentDate;
    private Date lastFollowUp;
    private Date referralDate;
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

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    /** Detached Java compatibility view; the wire uses the dedicated snapshot below. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.xml.bind.annotation.XmlTransient
    public ProfessionalSpecialist getConsultant() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.professionalSpecialistDomain(consultant); }

    @com.fasterxml.jackson.annotation.JsonProperty("consultant")
    @jakarta.xml.bind.annotation.XmlElement(name="consultant")
    public EmbeddedProfessionalSpecialistTo1 getConsultantSnapshot() { return consultant; }
    public void setConsultantSnapshot(EmbeddedProfessionalSpecialistTo1 value) { consultant = value; }

    public void setConsultant(ProfessionalSpecialist consultant) {
        this.consultant = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.professionalSpecialist(null, consultant);
    }

    /** Detached Java compatibility view; the wire uses the dedicated snapshot below. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.xml.bind.annotation.XmlTransient
    public Provider getMrp() { return io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.providerDomain(mrp); }

    @com.fasterxml.jackson.annotation.JsonProperty("mrp")
    @jakarta.xml.bind.annotation.XmlElement(name="mrp")
    public EmbeddedProviderTo1 getMrpSnapshot() { return mrp; }
    public void setMrpSnapshot(EmbeddedProviderTo1 value) { mrp = value; }

    public void setMrp(Provider mrp) {
        this.mrp = io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.provider(null, mrp);
    }
}
