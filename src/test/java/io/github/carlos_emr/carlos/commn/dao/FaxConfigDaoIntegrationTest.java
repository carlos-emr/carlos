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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link FaxConfigDao} fax-number lookups.
 *
 * <p>Uses fictitious 416555xxxx / 905555xxxx numbers and a placeholder access id only -
 * password fields are deliberately left empty so no encryption machinery is exercised.</p>
 *
 * @since 2026-08-21
 * @see FaxConfigDao
 */
@DisplayName("FaxConfig Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("fax")
@Transactional
public class FaxConfigDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FaxConfigDao faxConfigDao;

    @Test
    @Tag("read")
    @DisplayName("should return the config matching a fax number")
    void shouldReturnConfig_byNumber() {
        // Given
        FaxConfig saved = persistFaxConfig("4165550100", true);
        persistFaxConfig("4165550199", true);

        // When
        FaxConfig found = faxConfigDao.getConfigByNumber("4165550100");

        // Then: exact-number match regardless of active flag
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getFaxNumber()).isEqualTo("4165550100");

        // Then: unknown numbers yield null, not an exception
        assertThat(faxConfigDao.getConfigByNumber("9055550000")).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return active config by number only when active")
    void shouldReturnActiveConfig_byNumberOnlyWhenActive() {
        // Given: one inactive and one active config on distinct numbers
        persistFaxConfig("9055550111", false);
        FaxConfig activeConfig = persistFaxConfig("9055550122", true);

        // When / Then: the inactive row is invisible to the active lookup...
        assertThat(faxConfigDao.getActiveConfigByNumber("9055550111")).isNull();

        // ...while the plain lookup still sees it
        assertThat(faxConfigDao.getConfigByNumber("9055550111")).isNotNull();

        // When / Then: the active row is returned
        FaxConfig found = faxConfigDao.getActiveConfigByNumber("9055550122");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(activeConfig.getId());
        assertThat(found.isActive()).isTrue();
    }

    @Test
    @Tag("query")
    @DisplayName("should count all configs after persisting")
    void shouldCountAllConfigs_afterPersist() {
        // Given
        persistFaxConfig("4165550101", true);
        persistFaxConfig("4165550102", false);

        // When
        int count = faxConfigDao.getCountAll();

        // Then
        assertThat(count).isEqualTo(2);
    }

    // -- helper methods --

    private FaxConfig persistFaxConfig(String faxNumber, boolean active) {
        FaxConfig config = new FaxConfig();
        config.setFaxNumber(faxNumber);
        config.setActive(active);
        config.setDownload(active);
        config.setFaxUser("test-access-id");
        config.setAccountName("Test Fax Account " + faxNumber);
        config.setQueue(1);
        config.setProviderType(FaxConfig.ProviderType.SRFAX);
        faxConfigDao.persist(config);
        assertThat(config.getId()).isPositive();
        return config;
    }
}
