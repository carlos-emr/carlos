/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.billing.CA.BC.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Teleplan amounts for reports that must render when one stored field is corrupt. */
@Tag("unit")
@Tag("billing")
@DisplayName("Teleplan amounts in reports")
class TeleplanAmountUnitTest {

    @ParameterizedTest
    @CsvSource({"0001234,12.34", "000050},-5.00", "000123M,-12.34", "00001R,-0.19", "0,0.00", "' 0001234 ',12.34",
            "-0001234,-12.34", "99999999999,999999999.99"})
    @DisplayName("should decode implied cents and negative overpunch suffixes exactly")
    void shouldDecodeImpliedCentsAndOverpunch_forValidAmounts(String stored, String dollars) {
        assertThat(TeleplanAmount.known(stored)).isEqualTo(new BigDecimal(dollars));
        assertThat(TeleplanAmount.decode(stored)).isEqualTo(dollars);
        assertThat(TeleplanAmount.isUnparsed(stored)).isFalse();
        assertThat(TeleplanAmount.knownOrZero(stored)).isEqualTo(new BigDecimal(dollars));
        assertThat(TeleplanAmount.dollars(stored)).isEqualTo("$" + dollars);
        assertThat(TeleplanAmount.dollarsOrMark(stored)).isEqualTo("$" + dollars);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "       "})
    @DisplayName("should read a blank field as a real zero, as Teleplan sends for a held line")
    void shouldReadZero_forBlankAmounts(String stored) {
        assertThat(TeleplanAmount.known(stored)).isEqualTo(new BigDecimal("0.00"));
        assertThat(TeleplanAmount.isUnparsed(stored)).isFalse();
        assertThat(TeleplanAmount.dollars(stored)).isEqualTo("$0.00");
        assertThat(TeleplanAmount.unparsedNote("", stored)).isNull();
    }

    @Test
    @DisplayName("should read a missing field as a real zero")
    void shouldReadZero_forNullAmount() {
        assertThat(TeleplanAmount.known(null)).isEqualTo(new BigDecimal("0.00"));
        assertThat(TeleplanAmount.isUnparsed(null)).isFalse();
        assertThat(TeleplanAmount.unparsedCount((String) null)).isZero();
        assertThat(TeleplanAmount.dollarsOrMark(null)).isEqualTo("$0.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.5", "+123", "00012A", "1e3", "abc", "0000 12", "--12", "12}3", "\u0663\u0664"})
    @DisplayName("should never throw for a field that is not a Teleplan amount, and never count it as zero dollars")
    void shouldFlagWithoutThrowing_forMalformedAmounts(String stored) {
        // The strict decoder still refuses it; exports that must fail closed keep using it.
        assertThatThrownBy(() -> TeleplanAmount.decode(stored)).isInstanceOf(NumberFormatException.class);
        assertThat(TeleplanAmount.known(stored)).isNull();
        assertThat(TeleplanAmount.isUnparsed(stored)).isTrue();
        assertThat(TeleplanAmount.unparsedCount(stored)).isEqualTo(1);
        // Totals add nothing for it; the cell and the note show it is not an amount.
        assertThat(TeleplanAmount.knownOrZero(stored)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(TeleplanAmount.dollars(stored)).isEqualTo("Unparsed Teleplan amount: " + stored);
        assertThat(TeleplanAmount.dollarsOrMark(stored)).isEqualTo("?");
        assertThat(TeleplanAmount.unparsedNote("", stored)).isEqualTo("Unparsed Teleplan amount: " + stored);
    }

    @Test
    @DisplayName("should name each malformed field of a row with its column, and count them")
    void shouldNameEachMalformedField_withItsColumn() {
        assertThat(TeleplanAmount.unparsedNote("Gross Amt", "12.5", "Reg. Amt", "000030", "Adj. Out.", "+123", "Blnc. Fwd.", ""))
                .isEqualTo("Unparsed Teleplan amount: Gross Amt 12.5; Adj. Out. +123");
        assertThat(TeleplanAmount.unparsedNote("Gross Amt", "000100", "Reg. Amt", null)).isNull();
        assertThat(TeleplanAmount.unparsedCount("12.5", "000030", null, "", "+123")).isEqualTo(2);
        assertThat(TeleplanAmount.unparsedCount()).isZero();
    }

    @Test
    @DisplayName("should use the wording of the remittance CSV export")
    void shouldShareWording_withTheCsvExport() {
        assertThat(TeleplanAmount.UNPARSED_LABEL).isEqualTo("Unparsed Teleplan amount: ");
    }
}
