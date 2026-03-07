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
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProfessionalSpecialistDao} covering
 * findAll, findByEDataUrlNotNull, findByFullName, findByLastName,
 * findBySpecialty, findByReferralNo, and getByReferralNo.
 *
 * <p>Migrated from legacy {@code ProfessionalSpecialistDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProfessionalSpecialistDao
 */
@DisplayName("ProfessionalSpecialistDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ProfessionalSpecialistDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProfessionalSpecialistDao dao;

    private ProfessionalSpecialist createSpecialist(String firstName, String lastName) {
        ProfessionalSpecialist ps = new ProfessionalSpecialist();
        EntityDataGenerator.generateTestDataForModelClass(ps);
        ps.setDeleted(false);
        ps.setFirstName(firstName);
        ps.setLastName(lastName);
        dao.persist(ps);
        return ps;
    }

    @Nested
    @DisplayName("Read operations")
    class ReadOperations {

        @Test
        @Tag("read")
        @DisplayName("should return all non-deleted specialists ordered by last name")
        void shouldReturnAllSpecialists_whenFindAllCalled() {
            ProfessionalSpecialist ps1 = createSpecialist("FirstName2", "LastName2");
            ProfessionalSpecialist ps2 = createSpecialist("FirstName3", "LastName3");
            ProfessionalSpecialist ps3 = createSpecialist("FirstName1", "LastName1");

            List<ProfessionalSpecialist> result = dao.findAll();

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isEqualTo(ps3);
            assertThat(result.get(1)).isEqualTo(ps1);
            assertThat(result.get(2)).isEqualTo(ps2);
        }

        @Test
        @Tag("read")
        @DisplayName("should return only specialists with non-null eDataUrl ordered by last name")
        void shouldReturnSpecialists_whenEDataUrlNotNull() {
            ProfessionalSpecialist ps1 = createSpecialist("FirstName2", "LastName2");
            ps1.seteDataUrl("eData1");
            dao.merge(ps1);

            ProfessionalSpecialist ps2 = createSpecialist("FirstName3", "LastName3");
            ps2.seteDataUrl("eData2");
            dao.merge(ps2);

            ProfessionalSpecialist ps3 = createSpecialist("FirstName1", "LastName1");
            ps3.seteDataUrl("eData3");
            dao.merge(ps3);

            ProfessionalSpecialist ps4 = createSpecialist("FirstName4", "LastName4");
            ps4.seteDataUrl(null);
            dao.merge(ps4);

            List<ProfessionalSpecialist> result = dao.findByEDataUrlNotNull();

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isEqualTo(ps3);
            assertThat(result.get(1)).isEqualTo(ps1);
            assertThat(result.get(2)).isEqualTo(ps2);
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find specialists by full name (last name and first name)")
        void shouldFindSpecialists_byFullName() {
            ProfessionalSpecialist ps1 = createSpecialist("FirstName1", "LastName1");
            createSpecialist("FirstName3", "LastName3");
            ProfessionalSpecialist ps3 = createSpecialist("FirstName1", "LastName1");

            List<ProfessionalSpecialist> result = dao.findByFullName("LastName1", "FirstName1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(ps1);
            assertThat(result.get(1)).isEqualTo(ps3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find specialists by last name")
        void shouldFindSpecialists_byLastName() {
            ProfessionalSpecialist ps1 = createSpecialist("FirstName1", "LastName1");
            createSpecialist("FirstName3", "LastName3");
            ProfessionalSpecialist ps3 = createSpecialist("FirstName1", "LastName1");

            List<ProfessionalSpecialist> result = dao.findByLastName("LastName1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(ps1);
            assertThat(result.get(1)).isEqualTo(ps3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find specialists by specialty type ordered by last name")
        void shouldFindSpecialists_bySpecialty() {
            ProfessionalSpecialist ps1 = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(ps1);
            ps1.setDeleted(false);
            ps1.setSpecialtyType("alpha");
            ps1.setLastName("LastName2");
            dao.persist(ps1);

            ProfessionalSpecialist ps2 = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(ps2);
            ps2.setDeleted(false);
            ps2.setSpecialtyType("bravo");
            ps2.setLastName("LastName3");
            dao.persist(ps2);

            ProfessionalSpecialist ps3 = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(ps3);
            ps3.setDeleted(false);
            ps3.setSpecialtyType("alpha");
            ps3.setLastName("LastName1");
            dao.persist(ps3);

            List<ProfessionalSpecialist> result = dao.findBySpecialty("alpha");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(ps3);
            assertThat(result.get(1)).isEqualTo(ps1);
        }

        @Test
        @Tag("query")
        @DisplayName("should find specialists by referral number ordered by last name")
        void shouldFindSpecialists_byReferralNo() {
            ProfessionalSpecialist ps1 = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(ps1);
            ps1.setDeleted(false);
            ps1.setReferralNo("alpha");
            ps1.setLastName("LastName2");
            dao.persist(ps1);

            ProfessionalSpecialist ps2 = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(ps2);
            ps2.setDeleted(false);
            ps2.setReferralNo("bravo");
            ps2.setLastName("LastName3");
            dao.persist(ps2);

            ProfessionalSpecialist ps3 = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(ps3);
            ps3.setDeleted(false);
            ps3.setReferralNo("alpha");
            ps3.setLastName("LastName1");
            dao.persist(ps3);

            List<ProfessionalSpecialist> result = dao.findByReferralNo("alpha");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(ps3);
            assertThat(result.get(1)).isEqualTo(ps1);
        }

        @Test
        @Tag("query")
        @DisplayName("should get single specialist by referral number")
        void shouldGetSpecialist_byReferralNo() {
            ProfessionalSpecialist ps1 = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(ps1);
            ps1.setDeleted(false);
            ps1.setReferralNo("alpha");
            ps1.setLastName("LastName2");
            dao.persist(ps1);

            ProfessionalSpecialist ps2 = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(ps2);
            ps2.setDeleted(false);
            ps2.setSpecialtyType("bravo");
            ps2.setLastName("LastName3");
            dao.persist(ps2);

            ProfessionalSpecialist ps3 = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(ps3);
            ps3.setDeleted(false);
            ps3.setSpecialtyType("charlie");
            ps3.setLastName("LastName1");
            dao.persist(ps3);

            ProfessionalSpecialist result = dao.getByReferralNo("alpha");

            assertThat(result).isEqualTo(ps1);
        }
    }
}
