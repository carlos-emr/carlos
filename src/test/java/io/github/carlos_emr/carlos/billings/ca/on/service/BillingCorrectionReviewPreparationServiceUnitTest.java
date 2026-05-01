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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCorrectionReviewViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionLineCommand;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionValidationCommand;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("BillingCorrectionReviewPreparationService")
@Tag("unit")
@Tag("billing")
class BillingCorrectionReviewPreparationServiceUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldPrepareReviewModelWithPremiumCodesWithoutLegacySessionBeans() {
        ServiceCodeLoader serviceCodeLoader = Mockito.mock(ServiceCodeLoader.class);
        BillingCorrectionReviewPreparationService service =
                new BillingCorrectionReviewPreparationService(serviceCodeLoader, new BillingCorrectionReviewViewModelAssembler());

        when(serviceCodeLoader.getBillingCodeAttr("A001A"))
                .thenReturn(List.of("A001A", "Minor assessment", "10.00", "0.00"));
        when(serviceCodeLoader.getBillingCodeAttr("E411A"))
                .thenReturn(List.of("E411A", "Evening premium", ".00", "0.50"));

        BillingCorrectionValidationCommand command = new BillingCorrectionValidationCommand(
                "250|Diabetes",
                "Ref Doctor",
                "ROSTERED",
                true,
                "123456",
                true,
                "ON",
                "F",
                "00",
                Map.of(
                        "xml_billing_no", "42",
                        "xml_vdate", "2026-04-28",
                        "xml_appointment_date", "2026-04-28",
                        "xml_diagnostic_detail", "250|Diabetes"),
                List.of(
                        new BillingCorrectionLineCommand("A001A", "2", ""),
                        new BillingCorrectionLineCommand("E411A", "1", "")),
                "42",
                "1234567890",
                "1980-01-01",
                "00",
                "2026-04-28",
                "O",
                "0000",
                "999998",
                "2026-04-28",
                "2026-04-29",
                "Doe,Jane",
                "123 Main",
                "ON",
                "Toronto",
                "M1M1M1",
                "F");

        BillingCorrectionReviewViewModel model = service.prepareReview(command);

        assertThat(model.isDataLoaded()).isTrue();
        assertThat(model.getBillingNo()).isEqualTo("42");
        assertThat(model.getFormattedTotal()).isEqualTo("30.00");
        assertThat(model.getContent()).contains("<rdohip>123456</rdohip>");
        assertThat(model.getBillingItems())
                .extracting(BillingCorrectionReviewViewModel.Item::getServiceCode,
                        BillingCorrectionReviewViewModel.Item::getStoredFee)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A001A", "2000"),
                        org.assertj.core.groups.Tuple.tuple("E411A", "1000"));
    }
}
