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
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.Collections;
import java.util.List;

/**
 * Patient-adjacent presentation data for the Ontario billing form.
 */
public record BillingONPatientPresentation(
        boolean demoDobInvalid,
        String familyDoctor,
        String rosterStatus,
        int age,
        List<String> patientDx,
        String patientDxAddCode,
        String patientDxMatchCode,
        String billingRecommendations,
        List<BillingONFormViewModel.BillingHistoryEntry> billingHistory,
        List<BillingONFormViewModel.BillingHistoryRow> billingHistoryRows) {

    public static final BillingONPatientPresentation EMPTY = new BillingONPatientPresentation(
            false, "", "", 0, List.of(), "", "", "", List.of(), List.of());

    public BillingONPatientPresentation {
        familyDoctor = nullToEmpty(familyDoctor);
        rosterStatus = nullToEmpty(rosterStatus);
        patientDx = patientDx == null ? Collections.emptyList() : List.copyOf(patientDx);
        patientDxAddCode = nullToEmpty(patientDxAddCode);
        patientDxMatchCode = nullToEmpty(patientDxMatchCode);
        billingRecommendations = nullToEmpty(billingRecommendations);
        billingHistory = billingHistory == null ? Collections.emptyList() : List.copyOf(billingHistory);
        billingHistoryRows = billingHistoryRows == null ? Collections.emptyList() : List.copyOf(billingHistoryRows);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
