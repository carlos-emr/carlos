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

public class PathNetLabResults {
    /**
     * HQL aggregate for a report's status over its OBR rows. A report whose OBRs all carry one
     * status keeps it; a report mixing statuses with anything still pending (for example F with P
     * or I) is reported as {@code P}, never {@code F}: the alphabetical {@code MIN} alone ranks F
     * ahead of I and P and listed a partly pending report as Final (finding 165, #4552).
     * <ul>
     * <li>{@code MIN}/{@code MAX} skip nulls, so an OBR with no status beside an F would leave both
     * equal to F; the {@code COUNT} comparison makes such a report pending as well.</li>
     * <li>C (corrected) beside F keeps the previous {@code MIN} result {@code C}: both are
     * complete, and no valid HL7 result status sorts between them.</li>
     * </ul>
     */
    public static final String REPORT_STATUS_HQL =
            "CASE WHEN COUNT(obr.resultStatus) <> COUNT(obr.id) THEN 'P' "
                    + "WHEN MIN(obr.resultStatus) = MAX(obr.resultStatus) THEN MIN(obr.resultStatus) "
                    + "WHEN MIN(obr.resultStatus) = 'C' AND MAX(obr.resultStatus) = 'F' THEN 'C' "
                    + "ELSE 'P' END";

    private Hl7Msh hl7Msh;
    private Hl7Pid hl7Pid;
    private Hl7Orc hl7Orc;
    private Hl7Obr hl7Obr;
    private ProviderLabRoutingModel providerLabRouting;
    private PatientLabRouting patientLabRouting;
    // MIN(obr.resultStatus) remains a textual HL7 status, such as F or P.
    private String minResultStatus;

    public PathNetLabResults(Hl7Msh hl7Msh, Hl7Pid hl7Pid, Hl7Orc hl7Orc, Hl7Obr hl7Obr, ProviderLabRoutingModel providerLabRouting, String minResultStatus) {
        this.hl7Msh = hl7Msh;
        this.hl7Pid = hl7Pid;
        this.hl7Orc = hl7Orc;
        this.hl7Obr = hl7Obr;
        this.providerLabRouting = providerLabRouting;
        this.minResultStatus = minResultStatus;
    }

    public PathNetLabResults(Hl7Msh hl7Msh, Hl7Pid hl7Pid, Hl7Orc hl7Orc, Hl7Obr hl7Obr, PatientLabRouting patientLabRouting, String minResultStatus) {
        this.hl7Msh = hl7Msh;
        this.hl7Pid = hl7Pid;
        this.hl7Orc = hl7Orc;
        this.hl7Obr = hl7Obr;
        this.patientLabRouting = patientLabRouting;
        this.minResultStatus = minResultStatus;
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

    public String getMinResultStatus() {
        return minResultStatus;
    }

    public void setMinResultStatus(String minResultStatus) {
        this.minResultStatus = minResultStatus;
    }
}
