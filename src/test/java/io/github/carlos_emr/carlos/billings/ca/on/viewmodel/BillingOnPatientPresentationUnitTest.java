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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for patient presentation PHI redaction. */
@DisplayName("BillingOnPatientPresentation")
@Tag("unit")
@Tag("billing")
class BillingOnPatientPresentationUnitTest {

    @Test
    void shouldRedactPatientSensitiveFields_fromToString() {
        BillingOnPatientPresentation patient = new BillingOnPatientPresentation(
                true,
                "Smith",
                "RO",
                47,
                List.of("DX-LIST-SECRET"),
                "ADD-DX-SECRET",
                "MATCH-DX-SECRET",
                "Sensitive billing recommendation",
                List.of(new BillingOnFormViewModel.BillingHistoryEntry(
                        "2026-04-20", "03", "0000", "HISTORY-DX-SECRET")),
                List.of(new BillingOnFormViewModel.BillingHistoryRow(
                        "row-1", "2026-04-01", "2026-04-01", "A001",
                        "ROW-DX-SECRET", "2026-04-02")));

        String value = patient.toString();

        assertThat(value).contains(
                "demoDobInvalid=true",
                "familyDoctor=Smith",
                "rosterStatus=RO",
                "age=47",
                "patientDx=<1 redacted values>",
                "patientDxAddCode=<redacted>",
                "patientDxMatchCode=<redacted>",
                "billingRecommendations=<redacted>",
                "billingHistory=<1 redacted entries>",
                "billingHistoryRows=<1 redacted rows>");
        assertThat(value).doesNotContain(
                "DX-LIST-SECRET",
                "ADD-DX-SECRET",
                "MATCH-DX-SECRET",
                "Sensitive billing recommendation",
                "HISTORY-DX-SECRET",
                "BillingHistoryEntry",
                "A001",
                "ROW-DX-SECRET",
                "BillingHistoryRow");
    }
}
