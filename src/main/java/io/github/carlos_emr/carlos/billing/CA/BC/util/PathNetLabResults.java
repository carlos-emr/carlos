/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billing.CA.BC.util;

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Msh;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obr;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Orc;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;

/**
 * Value object aggregating HL7 segment data for BC PathNet lab results.
 * Used as a constructor expression target in JPA queries to project joined
 * HL7 MSH, PID, ORC, and OBR segments along with routing information
 * into a single result object.
 *
 * @since 2026-03-17
 */
public class PathNetLabResults {
    private Hl7Msh hl7Msh;
    private Hl7Pid hl7Pid;
    private Hl7Orc hl7Orc;
    private Hl7Obr hl7Obr;
    private ProviderLabRoutingModel providerLabRouting;
    private PatientLabRouting patientLabRouting;
    private Long minResultStatus;

    /**
     * Constructs a PathNetLabResults from provider lab routing data.
     *
     * @param hl7Msh Hl7Msh the message header segment
     * @param hl7Pid Hl7Pid the patient identification segment
     * @param hl7Orc Hl7Orc the common order segment
     * @param hl7Obr Hl7Obr the observation request segment
     * @param providerLabRouting ProviderLabRoutingModel the provider routing information
     * @param minResultStatus String the minimum result status, parsed as Long
     */
    public PathNetLabResults(Hl7Msh hl7Msh, Hl7Pid hl7Pid, Hl7Orc hl7Orc, Hl7Obr hl7Obr, ProviderLabRoutingModel providerLabRouting, String minResultStatus) {
        this.hl7Msh = hl7Msh;
        this.hl7Pid = hl7Pid;
        this.hl7Orc = hl7Orc;
        this.hl7Obr = hl7Obr;
        this.providerLabRouting = providerLabRouting;
        this.minResultStatus = Long.valueOf(minResultStatus);
    }

    /**
     * Constructs a PathNetLabResults from patient lab routing data.
     *
     * @param hl7Msh Hl7Msh the message header segment
     * @param hl7Pid Hl7Pid the patient identification segment
     * @param hl7Orc Hl7Orc the common order segment
     * @param hl7Obr Hl7Obr the observation request segment
     * @param patientLabRouting PatientLabRouting the patient routing information
     * @param minResultStatus String the minimum result status, parsed as Long
     */
    public PathNetLabResults(Hl7Msh hl7Msh, Hl7Pid hl7Pid, Hl7Orc hl7Orc, Hl7Obr hl7Obr, PatientLabRouting patientLabRouting, String minResultStatus) {
        this.hl7Msh = hl7Msh;
        this.hl7Pid = hl7Pid;
        this.hl7Orc = hl7Orc;
        this.hl7Obr = hl7Obr;
        this.patientLabRouting = patientLabRouting;
        this.minResultStatus = Long.valueOf(minResultStatus);
    }

    public Hl7Msh getHl7Msh() {
        return hl7Msh;
    }

    public void setHl7Msh(Hl7Msh hl7Msh) {
        this.hl7Msh = hl7Msh;
    }

    public Hl7Pid getHl7Pid() {
        return hl7Pid;
    }

    public void setHl7Pid(Hl7Pid hl7Pid) {
        this.hl7Pid = hl7Pid;
    }

    public Hl7Orc getHl7Orc() {
        return hl7Orc;
    }

    public void setHl7Orc(Hl7Orc hl7Orc) {
        this.hl7Orc = hl7Orc;
    }

    public Hl7Obr getHl7Obr() {
        return hl7Obr;
    }

    public void setHl7Obr(Hl7Obr hl7Obr) {
        this.hl7Obr = hl7Obr;
    }

    public ProviderLabRoutingModel getProviderLabRouting() {
        return providerLabRouting;
    }

    public void setProviderLabRouting(ProviderLabRoutingModel providerLabRouting) {
        this.providerLabRouting = providerLabRouting;
    }

    public PatientLabRouting getPatientLabRouting() {
        return patientLabRouting;
    }

    public void setPatientLabRouting(PatientLabRouting patientLabRouting) {
        this.patientLabRouting = patientLabRouting;
    }

    public Long getMinResultStatus() {
        return minResultStatus;
    }

    public void setMinResultStatus(Long minResultStatus) {
        this.minResultStatus = minResultStatus;
    }
}
