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
import io.github.carlos_emr.carlos.commn.model.DrugReason;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DrugReasonDao} covering addNewDrugReason,
 * hasReason, and getReasonsForDrugID.
 *
 * <p>Migrated from legacy {@code DrugReasonDaoTest} (JUnit 4 / DaoTestFixtures)
 * with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see DrugReasonDao
 */
@DisplayName("DrugReasonDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("prescription")
@Transactional
public class DrugReasonDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DrugReasonDao dao;

    @Nested
    @DisplayName("addNewDrugReason tests")
    @Tag("create")
    class AddNewDrugReason {

        /**
         * Ensures that addNewDrugReason() persists the passed in drug reason record.
         */
        @Test
        @DisplayName("should return true when drug reason is persisted successfully")
        void shouldReturnTrue_whenDrugReasonPersisted() {
            Integer drugId = 10;
            String codingSystem = "NDC";
            String code = "0123456789";
            boolean archivedFlag = true;

            DrugReason reason1 = new DrugReason();
            EntityDataGenerator.generateTestDataForModelClass(reason1);
            reason1.setDrugId(drugId);
            reason1.setCodingSystem(codingSystem);
            reason1.setCode(code);
            reason1.setArchivedFlag(archivedFlag);

            assertThat(dao.addNewDrugReason(reason1)).isTrue();
        }
    }

    @Nested
    @DisplayName("hasReason tests")
    @Tag("read")
    class HasReason {

        /**
         * Ensures that a drug reason exists if a record has
         * a coding system, code, drug id, archivedFlag.
         * hasReason() selects opposite of passed in boolean.
         */
        @Test
        @DisplayName("should return true when matching drug reason exists")
        void shouldReturnTrue_whenMatchingDrugReasonExists() {
            Integer drugId = 10;
            String codingSystem = "NDC";
            String code = "0123456789";
            boolean archivedFlag = true;

            DrugReason reason1 = new DrugReason();
            EntityDataGenerator.generateTestDataForModelClass(reason1);
            reason1.setDrugId(drugId);
            reason1.setCodingSystem(codingSystem);
            reason1.setCode(code);
            reason1.setArchivedFlag(archivedFlag);

            DrugReason reason2 = new DrugReason();
            EntityDataGenerator.generateTestDataForModelClass(reason2);
            reason2.setDrugId(drugId);
            reason2.setCodingSystem(codingSystem);
            reason2.setCode(code);
            reason2.setArchivedFlag(false);

            dao.persist(reason1);
            dao.persist(reason2);
            hibernateTemplate.flush();

            assertThat(dao.hasReason(drugId, codingSystem, code, archivedFlag)).isTrue();
        }
    }

    @Nested
    @DisplayName("getReasonsForDrugID tests")
    @Tag("read")
    class GetReasonsForDrugID {

        /**
         * Ensures that getReasonsForDrugID() returns a list of drug reasons where
         * drug id matches, and archivedFlag is opposite of the flag passed in.
         */
        @Test
        @DisplayName("should return reasons with opposite archived flag for matching drug id")
        void shouldReturnReasons_withOppositeArchivedFlagForDrugId() {
            Integer drugId = 10;
            boolean archivedFlag = true;

            // getReasonsForDrugID() selects opposite of passed in boolean; should not be selected.
            DrugReason reason1 = new DrugReason();
            EntityDataGenerator.generateTestDataForModelClass(reason1);
            reason1.setDrugId(drugId);
            reason1.setArchivedFlag(archivedFlag);

            // Wrong drug id; should not be selected.
            DrugReason reason2 = new DrugReason();
            EntityDataGenerator.generateTestDataForModelClass(reason2);
            reason2.setDrugId(11);
            reason2.setArchivedFlag(false);

            DrugReason reason3 = new DrugReason();
            EntityDataGenerator.generateTestDataForModelClass(reason3);
            reason3.setDrugId(drugId);
            reason3.setArchivedFlag(false);

            dao.persist(reason1);
            dao.persist(reason2);
            dao.persist(reason3);
            hibernateTemplate.flush();

            List<DrugReason> result = dao.getReasonsForDrugID(drugId, archivedFlag);
            List<DrugReason> expectedResult = Arrays.asList(reason3);

            assertThat(result).hasSameSizeAs(expectedResult);
            assertThat(result).containsAll(expectedResult);
        }
    }
}
