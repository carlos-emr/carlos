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
package io.github.carlos_emr.carlos.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class UsageReportSupportUnitTest {
    @ParameterizedTest
    @CsvSource({"6,1,16.67", "6,2,33.33", "6,6,100", "6,0,0", "0,0,---", "0,1,---"})
    void shouldFormatReadablePercentage_whenGivenCounts(int total, int count, String expected) {
        assertThat(UsageReportSupport.percentage(total, count)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-05-31", "2026-03-08", "2026-11-01", "2026-12-31"})
    @ResourceLock("java.util.TimeZone.default")
    void shouldIncludeWholeCalendarDay_whenEndDateCrossesBoundary(String value) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
            ZoneId zone = ZoneId.systemDefault();
            LocalDate date = LocalDate.parse(value);
            Date selected = Date.from(date.atStartOfDay(zone).toInstant());
            Date nextDay = Date.from(date.plusDays(1).atStartOfDay(zone).toInstant());
            assertThat(UsageReportSupport.exclusiveEnd(selected)).isEqualTo(nextDay);
            assertThat(UsageReportSupport.inclusiveEnd(selected).getTime()).isEqualTo(nextDay.getTime() - 1);
            assertThat(selected).isEqualTo(Date.from(date.atStartOfDay(zone).toInstant()));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void shouldPreserveNull_whenDateIsAbsent() {
        assertThat(UsageReportSupport.exclusiveEnd(null)).isNull();
        assertThat(UsageReportSupport.inclusiveEnd(null)).isNull();
    }

    @Test
    void shouldCountOntarioInvoicePatients_whenBillingRegionIsOntario() {
        BillingONCHeader1Dao invoices = mock(BillingONCHeader1Dao.class);
        Date start = new Date(0L);
        Date end = new Date(86_399_999L);
        when(invoices.getNumberOfDemographicsWithInvoicesForProvider("101", start, end, true)).thenReturn(4);

        assertThat(UsageReportSupport.billedPatientCount(true, invoices, "101", start, end)).isEqualTo(4);
        verify(invoices).getNumberOfDemographicsWithInvoicesForProvider("101", start, end, true);
    }

    /** billing_on_cheader1 exists only in the Ontario schema: asking a BC database for it answered HTTP 500. */
    @Test
    void shouldNotQueryOntarioInvoices_whenBillingRegionIsNotOntario() {
        BillingONCHeader1Dao invoices = mock(BillingONCHeader1Dao.class);

        assertThat(UsageReportSupport.billedPatientCount(false, invoices, "101", new Date(0L), new Date(1L))).isNull();
        verifyNoInteractions(invoices);
    }

    /** What the report page calls: the region comes from the installation's billregion property. */
    @ParameterizedTest
    @ValueSource(strings = {"BC", "AB", ""})
    @ResourceLock("io.github.carlos_emr.CarlosProperties")
    void shouldReportBillingAsNotAvailable_forNonOntarioBillregion(String region) {
        BillingONCHeader1Dao invoices = mock(BillingONCHeader1Dao.class);
        CarlosProperties properties = CarlosProperties.getInstance();
        String previous = properties.getProperty("billregion");
        try {
            properties.setProperty("billregion", region);
            assertThat(UsageReportSupport.billedPatientCount(invoices, "101", new Date(0L), new Date(1L))).isNull();
            verifyNoInteractions(invoices);

            properties.setProperty("billregion", "ON");
            when(invoices.getNumberOfDemographicsWithInvoicesForProvider("101", new Date(0L), new Date(1L), true)).thenReturn(0);
            assertThat(UsageReportSupport.billedPatientCount(invoices, "101", new Date(0L), new Date(1L))).isZero();
        } finally {
            if (previous == null) {
                properties.remove("billregion");
            } else {
                properties.setProperty("billregion", previous);
            }
        }
    }
}
