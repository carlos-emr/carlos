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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Verifies all payment-report queries use the same local calendar boundaries.
 * @since 2026-10-04
 */
@Tag("unit")
class BillingONPaymentDateRangeUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({"2026-03-08, 2026-03-09, 23", "2026-11-01, 2026-11-02, 25",
            "2028-02-29, 2028-03-01, 24", "2026-12-31, 2027-01-01, 24"})
    @ResourceLock("java.util.TimeZone.default")
    void shouldBindNextCalendarMidnightWithoutMutatingInput_whenEndDateIsInclusive(String day, String nextDay, long hours) {
        var originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
        try {
            var entityManager = mock(EntityManager.class);
            var query = mock(Query.class);
            when(entityManager.createQuery(anyString())).thenReturn(query);
            when(query.getResultList()).thenReturn(new ArrayList<>());
            var dao = new BillingONPaymentDaoImpl();
            ReflectionTestUtils.setField(dao, "entityManager", entityManager);
            var header = mock(BillingONCHeader1.class);
            when(header.getId()).thenReturn(42);
            Date start = Timestamp.valueOf(day + " 00:00:00");
            Date end = Timestamp.valueOf(day + " 12:34:56");
            long original = end.getTime();
            Date expected = new Date(Timestamp.valueOf(nextDay + " 00:00:00").getTime());

            dao.find3rdPartyPayRecordsByBill(header, start, end);
            dao.find3rdPartyPayRecordsByBills(List.of(42), start, end);

            var invoiceDao = new BillingONCHeader1DaoImpl();
            ReflectionTestUtils.setField(invoiceDao, "entityManager", entityManager);
            var provider = new io.github.carlos_emr.carlos.commn.model.Provider();
            provider.setProviderNo("111111");
            invoiceDao.get3rdPartyInvoiceByProvider(provider, start, end, java.util.Locale.CANADA);
            invoiceDao.get3rdPartyInvoiceByDate(start, end, java.util.Locale.CANADA);

            verify(query, times(3)).setParameter(2, start);
            verify(query).setParameter(1, start);
            verify(query).setParameter(2, expected);
            verify(query, times(3)).setParameter(3, expected);
            assertThat(expected.getTime() - start.getTime()).isEqualTo(hours * 60 * 60 * 1000);
            assertThat(end.getTime()).isEqualTo(original);
        } finally {
            TimeZone.setDefault(originalZone);
        }
    }
}
