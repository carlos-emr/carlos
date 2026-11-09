/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.entities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

/** BC amount and aging compatibility contracts.
 * @since 2026-11-09
 */
@Tag("unit")
class BcBillingValueContractsUnitTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"12d", "1f", "0x1p3", " 12.00 ", "NaN", "Infinity", "1e3", "1e999999999", "1e-999999999"})
    void shouldKeepNonDecimalArchiveAmountsUnknown_whenReadingHistory(String source) {
        BillHistory history = new BillHistory();
        history.setAmountText(source);
        assertThat(history.getAmountText()).isEqualTo(source);
        assertThat(history.hasKnownAmount()).isFalse();
    }

    @Test
    void shouldReplaceRawArchiveText_whenNumericAmountIsExplicitlyChanged() {
        BillHistory history = new BillHistory();
        history.setAmountText("old invalid");
        history.setAmount(12.34);
        assertThat(history.getAmountText()).isEqualTo("12.34");
        assertThat(history.hasKnownAmount()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"+12345", "1e5", "12.3", "--5", "1-2"})
    void shouldRefuseNonTeleplanSyntax_whenDecodingImpliedCents(String source) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanAmount.decode(source))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldDecodeSingleCharacterOverpunch_withoutLosingImpliedCents() {
        assertThat(io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanAmount.decode("}")).isEqualTo("0.00");
        assertThat(io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanAmount.decode("R")).isEqualTo("-0.09");
    }

    @Test
    void shouldPreserveUnknownArchiveAmount_withoutInventingZero() {
        BillHistory history = new BillHistory();
        history.setAmountText("bad value");
        assertThat(history.getAmountText()).isEqualTo("bad value");
        assertThat(history.hasKnownAmount()).isFalse();
        assertThat(history.getAmount()).isNaN();
        history.setAmountText("123456.78");
        assertThat(history.getAmountText()).isEqualTo("123456.78");
        assertThat(history.hasKnownAmount()).isTrue();
    }

    @Test
    void shouldAgeUsingOneReportDate_atAllBoundaries() {
        LocalDate today = LocalDate.of(2026, 11, 2);
        int[] days = {-1, 0, 30, 31, 60, 61, 90, 91};
        String[] buckets = {"1", "1", "1", "2", "2", "3", "3", "4"};
        for (int i = 0; i < days.length; i++) {
            assertThat(MSPBill.ageRange(today.minusDays(days[i]).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE), today))
                    .as("age %s", days[i]).isEqualTo(buckets[i]);
        }
        assertThat(MSPBill.ageRange("20260230", today)).isEqualTo("0");
        assertThat(MSPBill.ageRange(null, today)).isEqualTo("0");
        MSPBill bill = new MSPBill();
        bill.setServiceDateRange("4");
        assertThat(bill.getServiceDateRange()).isEqualTo("4");
    }
}
