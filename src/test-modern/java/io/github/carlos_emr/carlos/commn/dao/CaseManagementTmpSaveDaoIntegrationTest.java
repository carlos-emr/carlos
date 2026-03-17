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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CaseManagementTmpSaveDao} covering create,
 * find (without date), and find (with date).
 *
 * <p>Migrated from legacy {@code CaseManagementTmpSaveDaoTest}
 * (JUnit 4 / DaoTestFixtures) with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see CaseManagementTmpSaveDao
 */
@DisplayName("CaseManagementTmpSaveDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class CaseManagementTmpSaveDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    @Qualifier("caseManagementTmpSaveDao")
    private CaseManagementTmpSaveDao dao;

    @Nested
    @DisplayName("create tests")
    @Tag("create")
    class Create {

        @Test
        @DisplayName("should persist entity with generated id")
        void shouldPersistEntity_withGeneratedId() throws Exception {
            CaseManagementTmpSave entity = new CaseManagementTmpSave();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("find without date tests")
    @Tag("read")
    class FindNoDate {

        @Test
        @DisplayName("should return matching record when provider, demographic and program match")
        void shouldReturnMatchingRecord_whenProviderDemographicProgramMatch() throws Exception {
            String providerNo1 = "alpha";
            int demographicNo1 = 101, demographicNo2 = 202;
            int programId1 = 111, programId2 = 222;

            CaseManagementTmpSave cMTS1 = new CaseManagementTmpSave();
            EntityDataGenerator.generateTestDataForModelClass(cMTS1);
            cMTS1.setProviderNo(providerNo1);
            cMTS1.setDemographicNo(demographicNo1);
            cMTS1.setProgramId(programId2);
            dao.persist(cMTS1);

            CaseManagementTmpSave cMTS2 = new CaseManagementTmpSave();
            EntityDataGenerator.generateTestDataForModelClass(cMTS2);
            cMTS2.setProviderNo(providerNo1);
            cMTS2.setDemographicNo(demographicNo2);
            cMTS2.setProgramId(programId1);
            dao.persist(cMTS2);

            CaseManagementTmpSave cMTS3 = new CaseManagementTmpSave();
            EntityDataGenerator.generateTestDataForModelClass(cMTS3);
            cMTS3.setProviderNo(providerNo1);
            cMTS3.setDemographicNo(demographicNo1);
            cMTS3.setProgramId(programId1);
            dao.persist(cMTS3);
            hibernateTemplate.flush();

            CaseManagementTmpSave result = dao.find(providerNo1, demographicNo1, programId1);
            assertThat(result).isEqualTo(cMTS3);
        }
    }

    @Nested
    @DisplayName("find with date tests")
    @Tag("read")
    class FindDate {

        @Test
        @DisplayName("should return matching record when provider, demographic, program and date match")
        void shouldReturnMatchingRecord_whenProviderDemographicProgramDateMatch() throws Exception {
            DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

            Date date1 = new Date(dfm.parse("20110101").getTime());
            Date date2 = new Date(dfm.parse("20010101").getTime());
            Date date3 = new Date(dfm.parse("20100101").getTime());

            String providerNo1 = "alpha";
            int demographicNo1 = 101, demographicNo2 = 202;
            int programId1 = 111, programId2 = 222;

            CaseManagementTmpSave cMTS1 = new CaseManagementTmpSave();
            EntityDataGenerator.generateTestDataForModelClass(cMTS1);
            cMTS1.setProviderNo(providerNo1);
            cMTS1.setDemographicNo(demographicNo1);
            cMTS1.setProgramId(programId2);
            cMTS1.setUpdateDate(date1);
            dao.persist(cMTS1);

            CaseManagementTmpSave cMTS2 = new CaseManagementTmpSave();
            EntityDataGenerator.generateTestDataForModelClass(cMTS2);
            cMTS2.setProviderNo(providerNo1);
            cMTS2.setDemographicNo(demographicNo2);
            cMTS2.setProgramId(programId1);
            cMTS2.setUpdateDate(date2);
            dao.persist(cMTS2);

            CaseManagementTmpSave cMTS3 = new CaseManagementTmpSave();
            EntityDataGenerator.generateTestDataForModelClass(cMTS3);
            cMTS3.setProviderNo(providerNo1);
            cMTS3.setDemographicNo(demographicNo1);
            cMTS3.setProgramId(programId1);
            cMTS3.setUpdateDate(date3);
            dao.persist(cMTS3);
            hibernateTemplate.flush();

            CaseManagementTmpSave result = dao.find(providerNo1, demographicNo1, programId1, date3);
            assertThat(result).isEqualTo(cMTS3);
        }
    }
}
