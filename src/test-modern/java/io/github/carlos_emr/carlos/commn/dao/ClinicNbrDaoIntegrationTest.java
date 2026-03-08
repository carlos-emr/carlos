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
import io.github.carlos_emr.carlos.commn.model.ClinicNbr;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ClinicNbrDao} covering findAll,
 * removeEntry, and addEntry.
 *
 * <p>Migrated from legacy {@code ClinicNbrDaoTest}
 * (JUnit 4 / DaoTestFixtures) with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see ClinicNbrDao
 */
@DisplayName("ClinicNbrDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class ClinicNbrDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ClinicNbrDao dao;

    @Nested
    @DisplayName("findAll tests")
    @Tag("read")
    class FindAll {

        @Test
        @DisplayName("should return persisted entries in the list")
        void shouldReturnPersistedEntries_whenFindAllCalled() throws Exception {
            ClinicNbr nbr1 = new ClinicNbr();
            EntityDataGenerator.generateTestDataForModelClass(nbr1);
            nbr1.setNbrStatus("A");
            nbr1.setNbrValue("1");
            dao.persist(nbr1);
            hibernateTemplate.flush();

            ArrayList<ClinicNbr> result = dao.findAll();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getNbrStatus()).isEqualTo("A");
            assertThat(result.get(0).getNbrValue()).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("removeEntry tests")
    @Tag("update")
    class RemoveEntry {

        /**
         * Ensures that removeEntry() deletes records by setting the status to 'D'.
         */
        @Test
        @DisplayName("should set status to D when entry is removed")
        void shouldSetStatusToD_whenEntryRemoved() throws Exception {
            ClinicNbr nbr1 = new ClinicNbr();
            EntityDataGenerator.generateTestDataForModelClass(nbr1);
            nbr1.setNbrStatus("A");
            dao.persist(nbr1);
            hibernateTemplate.flush();

            dao.removeEntry(nbr1.getId());
            hibernateTemplate.flush();

            ClinicNbr found = dao.find(nbr1.getId());
            assertThat(found.getNbrStatus()).isEqualTo("D");
        }
    }

    @Nested
    @DisplayName("addEntry tests")
    @Tag("create")
    class AddEntry {

        /**
         * Ensures that addEntry() persists new records given value and string.
         */
        @Test
        @DisplayName("should persist entry with correct value and string")
        void shouldPersistEntry_withCorrectValueAndString() throws Exception {
            String nbrValue = "A";
            String nbrString = "RMA";

            ClinicNbr nbr1 = new ClinicNbr();
            EntityDataGenerator.generateTestDataForModelClass(nbr1);
            nbr1.setNbrString(nbrString);
            nbr1.setNbrValue(nbrValue);

            dao.addEntry(nbrValue, nbrString);

            assertThat(nbr1.getNbrString()).isEqualTo("RMA");
            assertThat(nbr1.getNbrValue()).isEqualTo("A");
        }
    }
}
