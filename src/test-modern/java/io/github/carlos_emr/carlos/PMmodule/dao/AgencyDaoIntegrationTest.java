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
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.PMmodule.model.Agency;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AgencyDao} persistence operations.
 *
 * <p>These tests validate that the AgencyDao correctly persists, retrieves,
 * and updates Agency entities. Tests use an H2 in-memory database and
 * are designed to catch Hibernate migration regressions.</p>
 *
 * @since 2026-02-09
 * @see AgencyDao
 * @see AgencyDaoImpl
 * @see Agency
 */
@DisplayName("AgencyDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class AgencyDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private AgencyDao agencyDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates a new Agency instance with the specified field values.
     *
     * @param intakeQuick Integer the intake quick form ID
     * @param quickState String the intake quick state code (max 2 chars)
     * @param intakeIndepth Integer the intake in-depth form ID (nullable)
     * @param indepthState String the intake in-depth state code (max 2 chars)
     * @return Agency a new Agency instance with the given values
     */
    private Agency createAgency(Integer intakeQuick, String quickState, Integer intakeIndepth, String indepthState) {
        Agency agency = new Agency();
        agency.setIntakeQuick(intakeQuick);
        agency.setIntakeQuickState(quickState);
        agency.setIntakeIndepth(intakeIndepth);
        agency.setIntakeIndepthState(indepthState);
        return agency;
    }

    @Test
    @Tag("create")
    @DisplayName("should save agency when valid data provided")
    void shouldSaveAgency_whenValidDataProvided() {
        // Given
        Agency agency = createAgency(1, "HS", 2, "AC");

        // When
        agencyDao.saveAgency(agency);
        entityManager.flush();

        // Then
        assertThat(agency.getId()).isNotNull();
        Agency found = entityManager.find(Agency.class, agency.getId());
        assertThat(found).isNotNull();
        assertThat(found.getIntakeQuick()).isEqualTo(1);
        assertThat(found.getIntakeQuickState()).isEqualTo("HS");
        assertThat(found.getIntakeIndepth()).isEqualTo(2);
        assertThat(found.getIntakeIndepthState()).isEqualTo("AC");
    }

    @Test
    @Tag("read")
    @DisplayName("should return agency when at least one exists")
    void shouldReturnAgency_whenAtLeastOneExists() {
        // Given
        Agency agency = createAgency(10, "AB", null, "CD");
        agencyDao.saveAgency(agency);
        entityManager.flush();

        // When
        Agency result = agencyDao.getLocalAgency();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when no agencies exist")
    void shouldReturnNull_whenNoAgenciesExist() {
        // When
        Agency result = agencyDao.getLocalAgency();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @Tag("create")
    @DisplayName("should throw exception when saving null")
    void shouldThrowException_whenSaveNull() {
        // When / Then
        assertThatThrownBy(() -> agencyDao.saveAgency(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("update")
    @DisplayName("should update agency when existing instance modified")
    void shouldUpdateAgency_whenExistingInstanceModified() {
        // Given
        Agency agency = createAgency(5, "HS", 3, "AC");
        agencyDao.saveAgency(agency);
        entityManager.flush();
        Long savedId = agency.getId();

        // When
        agency.setIntakeQuick(99);
        agency.setIntakeQuickState("ZZ");
        agencyDao.saveAgency(agency);
        entityManager.flush();
        entityManager.clear();

        // Then
        Agency updated = entityManager.find(Agency.class, savedId);
        assertThat(updated).isNotNull();
        assertThat(updated.getIntakeQuick()).isEqualTo(99);
        assertThat(updated.getIntakeQuickState()).isEqualTo("ZZ");
    }
}
