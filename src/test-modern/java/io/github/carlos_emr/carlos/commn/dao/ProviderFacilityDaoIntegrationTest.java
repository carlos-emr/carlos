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
import io.github.carlos_emr.carlos.commn.model.ProviderFacility;
import io.github.carlos_emr.carlos.commn.model.ProviderFacilityPK;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProviderFacilityDao} covering basic persistence
 * and retrieval of provider-facility associations.
 *
 * <p>Migrated from legacy {@code ProviderFacilityDaoTest} (JUnit 4).
 * The legacy test was @Ignored due to an unresolved issue; this modern test
 * re-enables the scenario.</p>
 *
 * @since 2026-03-07
 * @see ProviderFacilityDao
 */
@DisplayName("ProviderFacilityDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ProviderFacilityDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderFacilityDao dao;

    @Test
    @Tag("create")
    @Tag("read")
    @DisplayName("should persist and find provider facility by composite key")
    void shouldPersistAndFindProviderFacility_byCompositeKey() {
        // Given
        ProviderFacility entity = new ProviderFacility();
        ProviderFacilityPK pk = new ProviderFacilityPK();
        pk.setProviderNo("000001");
        pk.setFacilityId(1);
        entity.setId(pk);

        // When
        dao.persist(entity);
        hibernateTemplate.flush();

        // Then
        assertThat(entity.getId()).isNotNull();
        ProviderFacility found = dao.find(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId().getProviderNo()).isEqualTo("000001");
        assertThat(found.getId().getFacilityId()).isEqualTo(1);
    }
}
