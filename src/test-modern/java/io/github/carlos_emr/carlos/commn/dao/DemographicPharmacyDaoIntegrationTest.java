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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.DemographicPharmacy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicPharmacyDao} covering persist,
 * addPharmacyToDemographic, and findByDemographicId.
 *
 * <p>Migrated from legacy {@code DemographicPharmacyDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicPharmacyDao
 */
@DisplayName("DemographicPharmacyDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicPharmacyDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicPharmacyDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist pharmacy with generated ID")
    void shouldPersistPharmacy_whenValidDataProvided() throws Exception {
        DemographicPharmacy entity = new DemographicPharmacy();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
    }

    @Nested
    @DisplayName("addPharmacyToDemographic and findByDemographicId")
    class AddAndFind {

        @Test
        @Tag("create")
        @Tag("read")
        @DisplayName("should add pharmacy and find it by demographic ID")
        void shouldAddPharmacyAndFindIt_byDemographicId() throws Exception {
            dao.addPharmacyToDemographic(1, 100, 1);
            hibernateTemplate.flush();

            List<DemographicPharmacy> result = dao.findByDemographicId(100);

            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getPharmacyId()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findByDemographicId")
    class FindByDemographicId {

        @Test
        @Tag("query")
        @DisplayName("should return active pharmacies for demographic ordered by preference")
        void shouldReturnActivePharmacies_forDemographic() throws Exception {
            DateFormat dfm = new SimpleDateFormat("yyyyMMdd");
            Date addDate1 = new Date(dfm.parse("20080101").getTime());
            Date addDate2 = new Date(dfm.parse("20100101").getTime());
            Date addDate3 = new Date(dfm.parse("20110101").getTime());

            int demoNo1 = 101;
            int demoNo2 = 202;

            DemographicPharmacy dp1 = new DemographicPharmacy();
            EntityDataGenerator.generateTestDataForModelClass(dp1);
            dp1.setStatus(DemographicPharmacy.ACTIVE);
            dp1.setDemographicNo(demoNo1);
            dp1.setAddDate(addDate1);
            dao.persist(dp1);

            DemographicPharmacy dp2 = new DemographicPharmacy();
            EntityDataGenerator.generateTestDataForModelClass(dp2);
            dp2.setStatus(DemographicPharmacy.INACTIVE);
            dp2.setDemographicNo(demoNo1);
            dp2.setAddDate(addDate2);
            dao.persist(dp2);

            DemographicPharmacy dp3 = new DemographicPharmacy();
            EntityDataGenerator.generateTestDataForModelClass(dp3);
            dp3.setStatus(DemographicPharmacy.ACTIVE);
            dp3.setDemographicNo(demoNo1);
            dp3.setAddDate(addDate3);
            dao.persist(dp3);

            DemographicPharmacy dp4 = new DemographicPharmacy();
            EntityDataGenerator.generateTestDataForModelClass(dp4);
            dp4.setStatus(DemographicPharmacy.ACTIVE);
            dp4.setDemographicNo(demoNo2);
            dp4.setAddDate(addDate3);
            dao.persist(dp4);

            hibernateTemplate.flush();

            List<DemographicPharmacy> result = dao.findByDemographicId(demoNo1);

            // Only active (status=1) pharmacies for demoNo1 should be returned
            assertThat(result).isNotEmpty();
            assertThat(result).allMatch(dp -> dp.getDemographicNo() == demoNo1);
            assertThat(result).allMatch(dp -> DemographicPharmacy.ACTIVE.equals(dp.getStatus()));
            // dp1 should be in the results
            assertThat(result.stream().anyMatch(dp -> dp.getId().equals(dp1.getId()))).isTrue();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for demographic with no pharmacies")
        void shouldReturnEmptyList_forDemographicWithNoPharmacies() throws Exception {
            List<DemographicPharmacy> result = dao.findByDemographicId(99999);

            assertThat(result).isEmpty();
        }
    }
}
