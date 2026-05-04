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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.command;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit coverage for {@code BillingCorrectionCommand} request normalization and validation behavior. */
@DisplayName("Ontario billing correction commands")
@Tag("unit")
@Tag("billing")
class BillingCorrectionCommandUnitTest {

    @Test
    void shouldParseValidationCommandDatesAndLineAmounts_withValidInput() {
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
                Map.of("xml_billing_no", "42"),
                List.of(new BillingCorrectionLineCommand("A001A", "2.0", "10.00")),
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

        assertThat(command.dobText()).isEqualTo("1980-01-01");
        assertThat(command.visitDateText()).isEqualTo("2026-04-28");
        assertThat(command.serviceLines().get(0).billingUnitText()).isEqualTo("2");
        assertThat(command.serviceLines().get(0).billingAmount().format()).isEqualTo("10.00");
    }

    @Test
    void shouldParseSubmitCommandStoredCentsAndQuantity_withValidInput() {
        BillingCorrectionSubmitCommand command = new BillingCorrectionSubmitCommand(
                "42",
                "<rd>Ref Doctor</rd>",
                "3000",
                "1234567890",
                "1980-01-01",
                "00",
                "2026-04-28",
                "O",
                "0000",
                "999998",
                "2026-04-28",
                List.of(new BillingCorrectionSubmitItemCommand(
                        "A001A", "Minor assessment", "2000", "250", "2.0")));

        assertThat(command.total().format()).isEqualTo("30.00");
        assertThat(command.totalStored()).isEqualTo("3000");
        assertThat(command.items().get(0).serviceValue().format()).isEqualTo("20.00");
        assertThat(command.items().get(0).serviceValueStored()).isEqualTo("2000");
        assertThat(command.items().get(0).quantityText()).isEqualTo("2");
    }

    @Test
    void shouldNormalizeNullBillingUnitThroughQuantityParser_forCorrectionLine() {
        BillingCorrectionLineCommand command =
                new BillingCorrectionLineCommand("A001A", (java.math.BigDecimal) null, null);

        assertThat(command.billingUnitText()).isEqualTo("0");
    }

    @Test
    void shouldRejectMalformedDateAtCommandBoundary_forInvalidInput() {
        assertThatThrownBy(() -> new BillingCorrectionSubmitCommand(
                "42",
                "<rd>Ref Doctor</rd>",
                "3000",
                "1234567890",
                "not-a-date",
                "00",
                "2026-04-28",
                "O",
                "0000",
                "999998",
                "2026-04-28",
                List.of()))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("dob")
                .hasMessageContaining("not-a-date");
    }

    @Test
    void shouldSanitizeMalformedDateValue_inValidationMessage() {
        assertThatThrownBy(() -> new BillingCorrectionSubmitCommand(
                "42",
                "<rd>Ref Doctor</rd>",
                "3000",
                "1234567890",
                "not-a-date\r\nforged=true",
                "00",
                "2026-04-28",
                "O",
                "0000",
                "999998",
                "2026-04-28",
                List.of()))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("dob")
                .hasMessageNotContaining("\r")
                .hasMessageNotContaining("\n");
    }

    @Test
    void shouldTreatBlankSubmitTotalAsZero_withWhitespace() {
        BillingCorrectionSubmitCommand command = new BillingCorrectionSubmitCommand(
                "42",
                "<rd>Ref Doctor</rd>",
                " ",
                "1234567890",
                "",
                "00",
                "",
                "O",
                "0000",
                "999998",
                "",
                List.of());

        assertThat(command.totalStored()).isEqualTo("0");
        assertThat(command.total().format()).isEqualTo("0.00");
    }

    @Test
    void shouldAllowBlankOptionalDates_forValidationCommand() {
        BillingCorrectionValidationCommand command = new BillingCorrectionValidationCommand(
                "250|Diabetes",
                "Ref Doctor",
                "ROSTERED",
                false,
                "123456",
                false,
                "ON",
                "F",
                "00",
                Map.of(),
                List.of(),
                "42",
                "1234567890",
                " ",
                "00",
                "",
                "O",
                "0000",
                "999998",
                null,
                "",
                "Doe,Jane",
                "123 Main",
                "ON",
                "Toronto",
                "M1M1M1",
                "F");

        assertThat(command.dob()).isNull();
        assertThat(command.visitDate()).isNull();
        assertThat(command.billingDate()).isNull();
        assertThat(command.updateDate()).isNull();
        assertThat(command.dobText()).isEmpty();
        assertThat(command.visitDateText()).isEmpty();
    }

    @Test
    void shouldAllowBlankOptionalDates_forSubmitCommand() {
        BillingCorrectionSubmitCommand command = new BillingCorrectionSubmitCommand(
                "42",
                "<rd>Ref Doctor</rd>",
                "3000",
                "1234567890",
                "",
                "00",
                " ",
                "O",
                "0000",
                "999998",
                null,
                List.of());

        assertThat(command.dob()).isNull();
        assertThat(command.visitDate()).isNull();
        assertThat(command.billingDate()).isNull();
        assertThat(command.dobText()).isEmpty();
        assertThat(command.visitDateText()).isEmpty();
        assertThat(command.billingDateText()).isEmpty();
    }

    @Test
    void shouldParseBasicIsoDates_fromCorrectionForms() {
        BillingCorrectionValidationCommand command = new BillingCorrectionValidationCommand(
                "250|Diabetes",
                "Ref Doctor",
                "ROSTERED",
                false,
                "123456",
                false,
                "ON",
                "F",
                "00",
                Map.of(),
                List.of(),
                "42",
                "1234567890",
                "19800101",
                "00",
                "20260428",
                "O",
                "0000",
                "999998",
                "20260429",
                "20260430",
                "Doe,Jane",
                "123 Main",
                "ON",
                "Toronto",
                "M1M1M1",
                "F");

        assertThat(command.dobText()).isEqualTo("1980-01-01");
        assertThat(command.visitDateText()).isEqualTo("2026-04-28");
        assertThat(command.billingDateText()).isEqualTo("2026-04-29");
        assertThat(command.updateDateText()).isEqualTo("2026-04-30");
    }
}
