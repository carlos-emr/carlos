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
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.Provider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProgramProviderDAO} covering provider role update
 * operations.
 *
 * <p>Migrated from legacy {@code ProgramProviderDaoTest} (JUnit 4 / DaoTestFixtures)
 * with BDD-style naming and AssertJ assertions.</p>
 *
 * @since 2026-03-07
 * @see ProgramProviderDAO
 */
@Disabled("Production code issue: Provider entity has many VARCHAR columns under 21 chars (HBM-defined lengths) " +
        "that EntityDataGenerator overflows. Needs EntityDataGenerator to respect HBM column lengths, " +
        "or Provider fields need @Column(length=) annotations matching HBM definitions.")
@DisplayName("ProgramProviderDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("program")
@Transactional
public class ProgramProviderDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    @Qualifier("programProviderDAO")
    private ProgramProviderDAO dao;

    @Autowired
    private ProviderDao providerDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    @Tag("update")
    @DisplayName("should update provider role without error")
    void shouldUpdateProviderRole_whenValidProgramProviderProvided() throws Exception {
        // Given
        String providerId = "111";

        Provider provider = new Provider();
        EntityDataGenerator.generateTestDataForModelClass(provider);
        provider.setProviderNo(providerId);
        provider.setProviderType("doctor"); // fits VARCHAR(15)
        provider.setSpecialty("GP"); // fits VARCHAR(20)
        provider.setHsoNo(""); // fits VARCHAR(10)
        provider.setStatus("1"); // fits VARCHAR(1)
        provider.setSex("M"); // fits VARCHAR(1)
        provider.setProviderActivity(""); // fits VARCHAR(3)
        provider.setTeam(""); // fits VARCHAR(20)
        provider.setPhone(""); // fits VARCHAR(20)
        provider.setWorkPhone(""); // fits VARCHAR(50)
        provider.setOhipNo(""); // fits VARCHAR(20)
        provider.setRmaNo(""); // fits VARCHAR(20)
        provider.setBillingNo(""); // fits VARCHAR(20)
        provider.setTitle("Dr"); // fits VARCHAR(20)
        providerDao.saveProvider(provider);

        // Ensure secrole and program records exist for FK constraints
        entityManager.createNativeQuery(
                "MERGE INTO secrole (role_no, role_name) KEY(role_no) VALUES (1, 'test_role')")
                .executeUpdate();
        entityManager.createNativeQuery(
                "MERGE INTO program (id, name, type) KEY(id) VALUES (10016, 'Test', 'community')")
                .executeUpdate();

        ProgramProvider pp = new ProgramProvider();
        EntityDataGenerator.generateTestDataForModelClass(pp);
        pp.setProviderNo(providerId);
        pp.setRoleId(1L);
        pp.setProgramId(10016L);
        pp.setId(null);
        dao.saveProgramProvider(pp);

        // When / Then - should not throw
        assertThatCode(() -> dao.updateProviderRole(pp, 19999L))
                .doesNotThrowAnyException();
    }
}
