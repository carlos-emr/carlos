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

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionLineCommand;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionValidationCommand;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewDraft;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewItemDraft;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingCorrectionReviewPreparationService} review-page preparation and messaging. */
@DisplayName("BillingCorrectionReviewPreparationService")
@Tag("unit")
@Tag("billing")
class BillingCorrectionReviewPreparationServiceUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldPrepareReviewDraftWithPremiumCodes_withoutLegacySessionBeans() {
        ServiceCodeLoader serviceCodeLoader = Mockito.mock(ServiceCodeLoader.class);
        BillingCorrectionReviewPreparationService service =
                new BillingCorrectionReviewPreparationService(serviceCodeLoader);

        when(serviceCodeLoader.getBillingCodeAttr("A001A"))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute(
                        "A001A", "Minor assessment", "10.00", "0.00", "", "false")));
        when(serviceCodeLoader.getBillingCodeAttr("E411A"))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute(
                        "E411A", "Evening premium", ".00", "0.50", "", "false")));

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

        BillingCorrectionReviewDraft draft = service.prepareReviewDraft(command);

        assertThat(draft.dataLoaded()).isTrue();
        assertThat(draft.billingNo()).isEqualTo("42");
        assertThat(draft.total()).isEqualTo("3000");
        assertThat(draft.content()).contains("<rdohip>123456</rdohip>");
        assertThat(SxmlMisc.getXmlContent(draft.content(), "rdohip")).isEqualTo("123456");
        assertThat(draft.items())
                .extracting(BillingCorrectionReviewItemDraft::serviceCode,
                        BillingCorrectionReviewItemDraft::storedFee)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A001A", "2000"),
                        org.assertj.core.groups.Tuple.tuple("E411A", "1000"));
    }

    @Test
    void shouldEscapeContent_whenValuesContainMarkup() {
        ServiceCodeLoader serviceCodeLoader = Mockito.mock(ServiceCodeLoader.class);
        BillingCorrectionReviewPreparationService service =
                new BillingCorrectionReviewPreparationService(serviceCodeLoader);

        BillingCorrectionValidationCommand command = new BillingCorrectionValidationCommand(
                "250|Diabetes",
                "Dr <Referral>",
                "ROSTERED & ACTIVE",
                false,
                "123<456",
                false,
                "O<N",
                "F",
                "00",
                Map.of(
                        "xml_safe", "A&B <C>",
                        "xml_bad<tag", "should not render"),
                List.of(),
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

        BillingCorrectionReviewDraft draft = service.prepareReviewDraft(command);

        assertThat(draft.content()).contains("<rdohip></rdohip>");
        assertThat(draft.content()).contains("<hctype></hctype>");
        assertThat(draft.content()).contains("<demosex>F</demosex>");
        assertThat(draft.content()).contains("<rd>Dr &lt;Referral&gt;</rd>");
        assertThat(draft.content()).contains("<xml_roster>ROSTERED &amp; ACTIVE</xml_roster>");
        assertThat(draft.content()).contains("<xml_safe>A&amp;B &lt;C&gt;</xml_safe>");
        assertThat(SxmlMisc.getXmlContent(draft.content(), "rdohip")).isEmpty();
        assertThat(SxmlMisc.getXmlContent(draft.content(), "hctype")).isEmpty();
        assertThat(draft.content()).doesNotContain("123&lt;456");
        assertThat(draft.content()).doesNotContain("O&lt;N");
        assertThat(draft.content()).doesNotContain("xml_bad<tag");
        assertThat(draft.content()).doesNotContain("should not render");
    }

}
