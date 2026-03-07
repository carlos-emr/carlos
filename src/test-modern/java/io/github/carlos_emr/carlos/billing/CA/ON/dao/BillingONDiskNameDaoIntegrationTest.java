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

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONDiskName;
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
 * Integration tests for {@link BillingONDiskNameDao}.
 *
 * <p>Tests persist, findByGroupNo, getPrevDiskCreateDate, and
 * findByCreateDateRangeAndStatus methods with meaningful assertions.</p>
 *
 * @since 2026-03-07
 * @see BillingONDiskNameDao
 */
@DisplayName("BillingONDiskNameDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-on")
@Transactional
public class BillingONDiskNameDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONDiskNameDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        BillingONDiskName entity = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find by group number and return most recent record")
    void shouldFindByGroupNo_whenMatchingRecordExists() {
        BillingONDiskName entity1 = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity1);
        entity1.setGroupNo("GRP01");
        entity1.setCreateDateTime(new Date(1000000L));
        dao.persist(entity1);

        BillingONDiskName entity2 = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity2);
        entity2.setGroupNo("GRP01");
        entity2.setCreateDateTime(new Date(2000000L));
        dao.persist(entity2);

        BillingONDiskName other = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(other);
        other.setGroupNo("GRP99");
        other.setCreateDateTime(new Date(3000000L));
        dao.persist(other);

        BillingONDiskName result = dao.findByGroupNo("GRP01");

        assertThat(result).isNotNull();
        assertThat(result.getGroupNo()).isEqualTo("GRP01");
        // Should return most recent by createDateTime DESC
        assertThat(result.getId()).isEqualTo(entity2.getId());
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when no matching group number exists")
    void shouldReturnNull_whenNoMatchingGroupNo() {
        BillingONDiskName entity = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setGroupNo("EXISTS");
        dao.persist(entity);

        BillingONDiskName result = dao.findByGroupNo("NONEXISTENT");

        assertThat(result).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find previous disk create date before given date")
    void shouldFindPrevDiskCreateDate_whenOlderRecordExists() {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date oldDate = cal.getTime();

        cal.set(2025, Calendar.JUNE, 1, 0, 0, 0);
        Date midDate = cal.getTime();

        cal.set(2025, Calendar.DECEMBER, 1, 0, 0, 0);
        Date recentDate = cal.getTime();

        cal.set(2025, Calendar.AUGUST, 1, 0, 0, 0);
        Date queryDate = cal.getTime();

        BillingONDiskName old = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(old);
        old.setGroupNo("GRP01");
        old.setCreateDateTime(oldDate);
        dao.persist(old);

        BillingONDiskName mid = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(mid);
        mid.setGroupNo("GRP01");
        mid.setCreateDateTime(midDate);
        dao.persist(mid);

        BillingONDiskName recent = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(recent);
        recent.setGroupNo("GRP01");
        recent.setCreateDateTime(recentDate);
        dao.persist(recent);

        BillingONDiskName result = dao.getPrevDiskCreateDate(queryDate, "GRP01");

        assertThat(result).isNotNull();
        // Should return the most recent record BEFORE queryDate (midDate, not oldDate)
        assertThat(result.getId()).isEqualTo(mid.getId());
    }

    @Test
    @Tag("read")
    @DisplayName("should find by create date range and status")
    void shouldFindByCreateDateRangeAndStatus_whenMatchingRecordsExist() {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.MARCH, 15, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date inRangeDate = cal.getTime();

        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0);
        Date outOfRangeDate = cal.getTime();

        cal.set(2025, Calendar.MARCH, 1, 0, 0, 0);
        Date startDate = cal.getTime();

        cal.set(2025, Calendar.MARCH, 31, 23, 59, 59);
        Date endDate = cal.getTime();

        BillingONDiskName matching = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(matching);
        matching.setCreateDateTime(inRangeDate);
        matching.setStatus("A");
        dao.persist(matching);

        BillingONDiskName wrongStatus = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(wrongStatus);
        wrongStatus.setCreateDateTime(inRangeDate);
        wrongStatus.setStatus("D");
        dao.persist(wrongStatus);

        BillingONDiskName outOfRange = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(outOfRange);
        outOfRange.setCreateDateTime(outOfRangeDate);
        outOfRange.setStatus("A");
        dao.persist(outOfRange);

        List<BillingONDiskName> results = dao.findByCreateDateRangeAndStatus(startDate, endDate, "A");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(matching.getId());
        assertThat(results.get(0).getStatus()).isEqualTo("A");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no records match date range and status")
    void shouldReturnEmptyList_whenNoRecordsMatchDateRangeAndStatus() {
        BillingONDiskName entity = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setCreateDateTime(new Date());
        entity.setStatus("A");
        dao.persist(entity);

        Calendar cal = Calendar.getInstance();
        cal.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
        Date startDate = cal.getTime();
        cal.set(2000, Calendar.JANUARY, 31, 23, 59, 59);
        Date endDate = cal.getTime();

        List<BillingONDiskName> results = dao.findByCreateDateRangeAndStatus(startDate, endDate, "A");

        assertThat(results).isEmpty();
    }
}
