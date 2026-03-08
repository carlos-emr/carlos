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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

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
@DisplayName("ProgramProviderDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("program")
@Transactional
public class ProgramProviderDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProgramProviderDAO dao;

    @Autowired
    private ProviderDao providerDao;

    @Test
    @Tag("update")
    @DisplayName("should update provider role without error")
    void shouldUpdateProviderRole_whenValidProgramProviderProvided() throws Exception {
        // Given
        String providerId = "111";

        Provider provider = new Provider();
        EntityDataGenerator.generateTestDataForModelClass(provider);
        provider.setProviderNo(providerId);
        providerDao.saveProvider(provider);

        ProgramProvider pp = new ProgramProvider();
        EntityDataGenerator.generateTestDataForModelClass(pp);
        pp.setProviderNo(providerId);
        pp.setId(null);
        dao.saveProgramProvider(pp);

        // When / Then - should not throw
        assertThatCode(() -> dao.updateProviderRole(pp, 19999L))
                .doesNotThrowAnyException();
    }
}
