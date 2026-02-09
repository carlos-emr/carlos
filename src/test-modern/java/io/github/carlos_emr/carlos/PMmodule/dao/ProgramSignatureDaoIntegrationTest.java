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

import io.github.carlos_emr.carlos.PMmodule.model.ProgramSignature;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProgramSignatureDao} persistence operations.
 *
 * <p>These tests validate that the ProgramSignatureDao correctly persists,
 * retrieves, and queries ProgramSignature entities. Tests use an H2 in-memory
 * database and are designed to catch Hibernate migration regressions,
 * particularly around HQL positional parameter binding and date ordering.</p>
 *
 * @since 2026-02-09
 * @see ProgramSignatureDao
 * @see ProgramSignatureDaoImpl
 * @see ProgramSignature
 */
@DisplayName("ProgramSignatureDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramSignatureDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private ProgramSignatureDao programSignatureDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates a new ProgramSignature instance with the specified field values.
     *
     * @param programId Integer the program ID this signature belongs to
     * @param programName String the name of the program (max 70 chars)
     * @param providerId String the provider ID (max 6 chars)
     * @param providerName String the provider name (max 60 chars)
     * @param updateDate Date the update timestamp for this signature
     * @return ProgramSignature a new ProgramSignature instance with the given values
     */
    private ProgramSignature createSignature(Integer programId, String programName, String providerId, String providerName, Date updateDate) {
        ProgramSignature ps = new ProgramSignature();
        ps.setProgramId(programId);
        ps.setProgramName(programName);
        ps.setProviderId(providerId);
        ps.setProviderName(providerName);
        ps.setUpdateDate(updateDate);
        return ps;
    }

    @Test
    @Tag("create")
    @DisplayName("should save program signature when valid data provided")
    void shouldSaveProgramSignature_whenValidDataProvided() {
        // Given
        ProgramSignature ps = createSignature(100, "Test Program", "P001", "Dr. Smith", new Date());

        // When
        programSignatureDao.saveProgramSignature(ps);

        // Then
        assertThat(ps.getId()).isNotNull();
        assertThat(ps.getId()).isGreaterThan(0);
        ProgramSignature found = entityManager.find(ProgramSignature.class, ps.getId());
        assertThat(found).isNotNull();
        assertThat(found.getProgramId()).isEqualTo(100);
        assertThat(found.getProgramName()).isEqualTo("Test Program");
        assertThat(found.getProviderId()).isEqualTo("P001");
        assertThat(found.getProviderName()).isEqualTo("Dr. Smith");
    }

    @Test
    @Tag("read")
    @DisplayName("should return first signature when multiple exist")
    void shouldReturnFirstSignature_whenMultipleExist() {
        // Given - save signatures with different dates; earliest should be returned
        Date yesterday = new Date(System.currentTimeMillis() - 86400000L);
        Date now = new Date();

        ProgramSignature earliest = createSignature(200, "Program A", "P002", "Dr. Jones", yesterday);
        ProgramSignature latest = createSignature(200, "Program A", "P003", "Dr. Brown", now);

        // Save with explicit dates set before the DAO overwrites them
        // Note: saveProgramSignature sets updateDate to now, so we persist directly
        entityManager.persist(earliest);
        entityManager.persist(latest);
        entityManager.flush();

        // When
        ProgramSignature result = programSignatureDao.getProgramFirstSignature(200);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(earliest.getId());
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when program ID is null")
    void shouldReturnNull_whenProgramIdIsNull() {
        // When
        ProgramSignature result = programSignatureDao.getProgramFirstSignature(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when program ID is zero or negative")
    void shouldReturnNull_whenProgramIdIsZeroOrNegative() {
        // When
        ProgramSignature resultZero = programSignatureDao.getProgramFirstSignature(0);
        ProgramSignature resultNegative = programSignatureDao.getProgramFirstSignature(-1);

        // Then
        assertThat(resultZero).isNull();
        assertThat(resultNegative).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return all signatures when multiple exist for program")
    void shouldReturnAllSignatures_whenMultipleExistForProgram() {
        // Given
        Date yesterday = new Date(System.currentTimeMillis() - 86400000L);
        Date now = new Date();

        ProgramSignature sig1 = createSignature(300, "Program B", "P010", "Dr. Alpha", yesterday);
        ProgramSignature sig2 = createSignature(300, "Program B", "P011", "Dr. Beta", now);
        ProgramSignature sigOther = createSignature(999, "Other Program", "P099", "Dr. Other", now);

        entityManager.persist(sig1);
        entityManager.persist(sig2);
        entityManager.persist(sigOther);
        entityManager.flush();

        // When
        List<ProgramSignature> results = programSignatureDao.getProgramSignatures(300);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(ProgramSignature::getProgramId)
                .containsOnly(300);
        // Should be ordered by updateDate ASC (earliest first)
        assertThat(results.get(0).getUpdateDate())
                .isBeforeOrEqualTo(results.get(1).getUpdateDate());
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when no signatures for program")
    void shouldReturnNull_whenNoSignaturesForProgram() {
        // When
        List<ProgramSignature> results = programSignatureDao.getProgramSignatures(99999);

        // Then
        // The DAO returns null for invalid programId, and empty list is not expected
        // for a valid programId with no results; however, since 99999 > 0 the HQL runs
        // and returns an empty list
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("create")
    @DisplayName("should set update date when saving")
    void shouldSetUpdateDate_whenSaving() {
        // Given
        ProgramSignature ps = createSignature(400, "Program C", "P020", "Dr. Gamma", null);
        Date beforeSave = new Date();

        // When
        programSignatureDao.saveProgramSignature(ps);

        // Then - saveProgramSignature sets updateDate to new Date()
        assertThat(ps.getUpdateDate()).isNotNull();
        assertThat(ps.getUpdateDate()).isAfterOrEqualTo(beforeSave);
    }

    @Test
    @Tag("create")
    @DisplayName("should throw exception when saving null")
    void shouldThrowException_whenSaveNull() {
        // When / Then
        assertThatThrownBy(() -> programSignatureDao.saveProgramSignature(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
