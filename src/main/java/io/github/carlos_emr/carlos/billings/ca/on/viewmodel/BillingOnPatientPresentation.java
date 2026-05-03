/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingViewStrings;

import java.util.Collections;
import java.util.List;

/**
 * Patient-adjacent presentation data for the Ontario billing form.
 */
public record BillingOnPatientPresentation(
        boolean demoDobInvalid,
        String familyDoctor,
        String rosterStatus,
        int age,
        List<String> patientDx,
        String patientDxAddCode,
        String patientDxMatchCode,
        String billingRecommendations,
        List<BillingOnFormViewModel.BillingHistoryEntry> billingHistory,
        List<BillingOnFormViewModel.BillingHistoryRow> billingHistoryRows) {

    public static final BillingOnPatientPresentation EMPTY = new BillingOnPatientPresentation(
            false, "", "", 0, List.of(), "", "", "", List.of(), List.of());

    public BillingOnPatientPresentation {
        familyDoctor = BillingViewStrings.nullToEmpty(familyDoctor);
        rosterStatus = BillingViewStrings.nullToEmpty(rosterStatus);
        patientDx = patientDx == null ? Collections.emptyList() : List.copyOf(patientDx);
        patientDxAddCode = BillingViewStrings.nullToEmpty(patientDxAddCode);
        patientDxMatchCode = BillingViewStrings.nullToEmpty(patientDxMatchCode);
        billingRecommendations = BillingViewStrings.nullToEmpty(billingRecommendations);
        billingHistory = billingHistory == null ? Collections.emptyList() : List.copyOf(billingHistory);
        billingHistoryRows = billingHistoryRows == null ? Collections.emptyList() : List.copyOf(billingHistoryRows);
    }

    @Override
    public String toString() {
        return "BillingOnPatientPresentation["
                + "demoDobInvalid=" + demoDobInvalid + ", "
                + "familyDoctor=" + familyDoctor + ", "
                + "rosterStatus=" + rosterStatus + ", "
                + "age=" + age + ", "
                + "patientDx=<" + patientDx.size() + " redacted values>, "
                + "patientDxAddCode=<redacted>, "
                + "patientDxMatchCode=<redacted>, "
                + "billingRecommendations=<redacted>, "
                + "billingHistory=<" + billingHistory.size() + " redacted entries>, "
                + "billingHistoryRows=<" + billingHistoryRows.size() + " redacted rows>]";
    }
}
