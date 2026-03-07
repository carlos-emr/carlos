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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.CdsFormOption;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CdsFormOptionDao} covering full method coverage
 * matching the legacy {@code CdsFormOptionDaoTest}.
 *
 * <p>Note: The legacy tests for findByVersionAndCategory and findByVersion were
 * commented out (ignored) because CdsFormOption has no setters for its fields.
 * These modern tests verify the DAO methods execute without error using empty
 * queries, matching the legacy test's effective coverage.</p>
 *
 * @since 2026-03-07
 * @see CdsFormOptionDao
 */
@DisplayName("CdsFormOption Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CdsFormOptionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CdsFormOptionDao dao;

    @Test
    @Tag("read")
    @DisplayName("should execute findByVersionAndCategory without error")
    void shouldExecuteFindByVersionAndCategory_withoutError() {
        List<CdsFormOption> result = dao.findByVersionAndCategory("1.1.0", "Basic");

        assertThat(result).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should execute findByVersion without error")
    void shouldExecuteFindByVersion_withoutError() {
        List<CdsFormOption> result = dao.findByVersion("1.1.0");

        assertThat(result).isNotNull();
    }
}
