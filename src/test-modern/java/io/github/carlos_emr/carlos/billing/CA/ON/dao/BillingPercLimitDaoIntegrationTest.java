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
package io.github.carlos_emr.carlos.billing.CA.ON.dao;

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingPercLimitDao}.
 *
 * <p>Tests persist, findByServiceCode, findByServiceCodeAndEffectiveDate,
 * and findByServiceCodeAndLatestDate methods with meaningful assertions.</p>
 *
 * @since 2026-03-07
 * @see BillingPercLimitDao
 */
@DisplayName("BillingPercLimitDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-on")
@Transactional
public class BillingPercLimitDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingPercLimitDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        BillingPercLimit entity = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find all records by service code")
    void shouldFindByServiceCode_whenMatchingRecordsExist() {
        BillingPercLimit limit1 = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(limit1);
        limit1.setService_code("A001");
        limit1.setMin("10");
        limit1.setMax("100");
        dao.persist(limit1);

        BillingPercLimit limit2 = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(limit2);
        limit2.setService_code("A001");
        limit2.setMin("20");
        limit2.setMax("200");
        dao.persist(limit2);

        BillingPercLimit other = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(other);
        other.setService_code("B002");
        other.setMin("30");
        other.setMax("300");
        dao.persist(other);

        List<BillingPercLimit> results = dao.findByServiceCode("A001");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(BillingPercLimit::getService_code)
                .containsOnly("A001");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no records match service code")
    void shouldReturnEmptyList_whenNoRecordsMatchServiceCode() {
        BillingPercLimit entity = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setService_code("EXISTS");
        dao.persist(entity);

        List<BillingPercLimit> results = dao.findByServiceCode("NOMATCH");

        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find record by service code and effective date")
    void shouldFindByServiceCodeAndEffectiveDate_whenExactMatchExists() {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.JUNE, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date effectiveDate = cal.getTime();

        BillingPercLimit match = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(match);
        match.setService_code("C003");
        match.setEffective_date(effectiveDate);
        match.setMin("50");
        match.setMax("500");
        dao.persist(match);

        BillingPercLimit otherDate = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(otherDate);
        otherDate.setService_code("C003");
        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0);
        otherDate.setEffective_date(cal.getTime());
        otherDate.setMin("10");
        otherDate.setMax("100");
        dao.persist(otherDate);

        BillingPercLimit result = dao.findByServiceCodeAndEffectiveDate("C003", effectiveDate);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(match.getId());
        assertThat(result.getService_code()).isEqualTo("C003");
        assertThat(result.getMin()).isEqualTo("50");
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when no record matches service code and effective date")
    void shouldReturnNull_whenNoRecordMatchesServiceCodeAndEffectiveDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.JUNE, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);

        BillingPercLimit result = dao.findByServiceCodeAndEffectiveDate("NONE", cal.getTime());

        assertThat(result).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by service code and latest date not exceeding query date")
    void shouldFindByServiceCodeAndLatestDate_whenMatchingRecordsExist() {
        Calendar cal = Calendar.getInstance();

        // Create a record with an old effective date
        cal.set(2024, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date oldDate = cal.getTime();

        BillingPercLimit oldLimit = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(oldLimit);
        oldLimit.setService_code("D004");
        oldLimit.setEffective_date(oldDate);
        oldLimit.setMin("10");
        dao.persist(oldLimit);

        // Create a record with a more recent effective date
        cal.set(2025, Calendar.JUNE, 1, 0, 0, 0);
        Date recentDate = cal.getTime();

        BillingPercLimit recentLimit = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(recentLimit);
        recentLimit.setService_code("D004");
        recentLimit.setEffective_date(recentDate);
        recentLimit.setMin("20");
        dao.persist(recentLimit);

        // Create a record with a future effective date
        cal.set(2026, Calendar.DECEMBER, 1, 0, 0, 0);
        Date futureDate = cal.getTime();

        BillingPercLimit futureLimit = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(futureLimit);
        futureLimit.setService_code("D004");
        futureLimit.setEffective_date(futureDate);
        futureLimit.setMin("30");
        dao.persist(futureLimit);

        // Query with a date between recentDate and futureDate
        cal.set(2025, Calendar.SEPTEMBER, 1, 0, 0, 0);
        Date queryDate = cal.getTime();

        List<BillingPercLimit> results = dao.findByServiceCodeAndLatestDate("D004", queryDate);

        // Should return only the record with recentDate (the MAX effective_date <= queryDate)
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(recentLimit.getId());
        assertThat(results.get(0).getMin()).isEqualTo("20");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no records have effective date before query date")
    void shouldReturnEmptyList_whenNoRecordsHaveEffectiveDateBeforeQueryDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.JUNE, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);

        BillingPercLimit futureOnly = new BillingPercLimit();
        EntityDataGenerator.generateTestDataForModelClass(futureOnly);
        futureOnly.setService_code("E005");
        futureOnly.setEffective_date(cal.getTime());
        dao.persist(futureOnly);

        cal.set(2024, Calendar.JANUARY, 1, 0, 0, 0);
        Date earlyQueryDate = cal.getTime();

        List<BillingPercLimit> results = dao.findByServiceCodeAndLatestDate("E005", earlyQueryDate);

        assertThat(results).isEmpty();
    }
}
