/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.MSP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@DisplayName("MSP extract billing date filters")
class ExtractBeanUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldParameterize_legacyDateRangeFragment() {
        ExtractBean.BillingDateFilter filter = ExtractBean.parseDateRangeFragment(
                " and ( to_days(service_date) >= to_days('2026-01-01')) "
                        + "and ( to_days(service_date) <= to_days('2026-01-31')) ");

        assertThat(filter.sql()).isEqualTo(
                " and to_days(service_date) >= to_days(?) and to_days(service_date) <= to_days(?)");
        assertThat(filter.params()).containsExactly("2026-01-01", "2026-01-31");
        assertThat(filter.withLeading("123456")).containsExactly("123456", "2026-01-01", "2026-01-31");
    }

    @Test
    void shouldAccept_directLegacyDateComparisons() {
        ExtractBean.BillingDateFilter filter = ExtractBean.parseDateRangeFragment(
                " and billing_date >= '2026-01-01' and billing_date < '2026-02-01'");

        assertThat(filter.sql()).isEqualTo(
                " and to_days(billing_date) >= to_days(?) and to_days(billing_date) < to_days(?)");
        assertThat(filter.params()).containsExactly("2026-01-01", "2026-02-01");
    }

    @Test
    void shouldReject_unsafeDateRangeFragments() {
        assertThatThrownBy(() -> ExtractBean.parseDateRangeFragment(
                " and billing_date >= '2026-01-01'; delete from billing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReject_invalidCalendarDates() {
        assertThatThrownBy(() -> ExtractBean.parseDateRangeFragment(
                " and billing_date >= '2026-99-99'"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
