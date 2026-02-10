/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.daos.security;

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SecuserroleDao (security module).
 *
 * <p>These tests validate HQL queries with positional parameters and
 * CRUD operations for the Secuserrole entity.</p>
 *
 * @since 2026-02-03
 * @see SecuserroleDao
 */
@DisplayName("SecuserroleDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecuserroleDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private SecuserroleDao secuserroleDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Secuserrole createSecuserrole(String providerNo, String roleName, String orgcd) {
        Secuserrole role = new Secuserrole();
        role.setProviderNo(providerNo);
        role.setRoleName(roleName);
        role.setOrgcd(orgcd);
        secuserroleDao.save(role);
        return role;
    }

    private Secuserrole createSecuserroleWithActive(String providerNo, String roleName,
                                                     String orgcd, Integer activeyn) {
        Secuserrole role = new Secuserrole();
        role.setProviderNo(providerNo);
        role.setRoleName(roleName);
        role.setOrgcd(orgcd);
        role.setActiveyn(activeyn);
        secuserroleDao.save(role);
        return role;
    }

    /** Tests for CRUD persistence operations. */
    @Nested
    @DisplayName("CRUD persistence operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist secuserrole with valid data")
        void shouldPersistSecuserrole_whenValidDataProvided() {
            // Given
            Secuserrole role = new Secuserrole();
            role.setProviderNo("P100");
            role.setRoleName("doctor");
            role.setOrgcd("ORG1");

            // When
            secuserroleDao.save(role);
            entityManager.flush();

            // Then
            assertThat(role.getId()).isNotNull();
            Secuserrole found = secuserroleDao.findById(role.getId());
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("P100");
        }

        @Test
        @Tag("create")
        @DisplayName("should save all secuserroles in batch")
        @SuppressWarnings("unchecked")
        void shouldSaveAll_whenBatchProvided() {
            // Given
            List batch = new ArrayList();
            for (int i = 0; i < 3; i++) {
                Secuserrole role = new Secuserrole();
                role.setProviderNo("BATCH" + i);
                role.setRoleName("role" + i);
                role.setOrgcd("ORG1");
                batch.add(role);
            }

            // When
            secuserroleDao.saveAll(batch);
            entityManager.flush();

            // Then
            List<Secuserrole> results = secuserroleDao.findAll();
            assertThat(results)
                .extracting(Secuserrole::getProviderNo)
                .contains("BATCH0", "BATCH1", "BATCH2");
        }

        @Test
        @Tag("update")
        @DisplayName("should update role name by ID")
        void shouldUpdateRoleName_byId() {
            // Given
            Secuserrole role = createSecuserrole("P200", "doctor", "ORG1");
            entityManager.flush();

            // When
            secuserroleDao.updateRoleName(role.getId(), "specialist");
            entityManager.flush();
            entityManager.clear();

            // Then
            Secuserrole found = secuserroleDao.findById(role.getId());
            assertThat(found.getRoleName()).isEqualTo("specialist");
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete secuserrole by entity reference")
        void shouldDeleteSecuserrole_whenEntityProvided() {
            // Given
            Secuserrole role = createSecuserrole("P300", "doctor", "ORG1");
            entityManager.flush();
            Integer savedId = role.getId();

            // When
            secuserroleDao.delete(role);
            entityManager.flush();

            // Then
            Secuserrole found = secuserroleDao.findById(savedId);
            assertThat(found).isNull();
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete secuserrole by ID")
        void shouldDeleteSecuserrole_byId() {
            // Given
            Secuserrole role = createSecuserrole("P301", "nurse", "ORG1");
            entityManager.flush();
            Integer savedId = role.getId();

            // When
            int deleted = secuserroleDao.deleteById(savedId);

            // Then
            assertThat(deleted).isEqualTo(1);
        }

        @Test
        @Tag("update")
        @DisplayName("should merge detached secuserrole instance")
        void shouldMergeSecuserrole_whenDetachedInstanceProvided() {
            // Given
            Secuserrole role = createSecuserrole("P400", "doctor", "ORG1");
            entityManager.flush();

            // When
            role.setRoleName("merged_role");
            Secuserrole merged = secuserroleDao.merge(role);
            entityManager.flush();

            // Then
            assertThat(merged).isNotNull();
            assertThat(merged.getRoleName()).isEqualTo("merged_role");
        }

        @Test
        @Tag("update")
        @DisplayName("should update active status via update method")
        void shouldUpdateActiveStatus_viaUpdateMethod() {
            // Given
            Secuserrole role = createSecuserroleWithActive("P500", "doctor", "ORG1", 1);
            entityManager.flush();

            // When
            role.setActiveyn(0);
            int rowsUpdated = secuserroleDao.update(role);
            entityManager.flush();

            // Then
            assertThat(rowsUpdated).isGreaterThanOrEqualTo(1);
        }
    }

    /** Tests for query operations. */
    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("read")
        @DisplayName("should find secuserrole by ID")
        void shouldFindById_whenValidIdProvided() {
            // Given
            Secuserrole saved = createSecuserrole("P600", "doctor", "ORG1");
            entityManager.flush();

            // When
            Secuserrole found = secuserroleDao.findById(saved.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("P600");
            assertThat(found.getRoleName()).isEqualTo("doctor");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null for non-existent ID")
        void shouldReturnNull_whenIdDoesNotExist() {
            // When
            Secuserrole found = secuserroleDao.findById(999999);

            // Then
            assertThat(found).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find secuserroles by provider number")
        @SuppressWarnings("unchecked")
        void shouldFindByProviderNo() {
            // Given
            createSecuserrole("P700", "doctor", "ORG1");
            createSecuserrole("P700", "nurse", "ORG2");
            createSecuserrole("P701", "admin", "ORG1");
            entityManager.flush();

            // When
            List<Secuserrole> results = secuserroleDao.findByProviderNo("P700");

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(r -> r.getProviderNo().equals("P700"));
        }

        @Test
        @Tag("read")
        @DisplayName("should find secuserroles by role name")
        @SuppressWarnings("unchecked")
        void shouldFindByRoleName() {
            // Given
            createSecuserrole("P800", "specialist", "ORG1");
            createSecuserrole("P801", "specialist", "ORG2");
            createSecuserrole("P802", "nurse", "ORG1");
            entityManager.flush();

            // When
            List<Secuserrole> results = secuserroleDao.findByRoleName("specialist");

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(r -> r.getRoleName().equals("specialist"));
        }

        @Test
        @Tag("filter")
        @DisplayName("should filter by active status")
        @SuppressWarnings("unchecked")
        void shouldFilterByActiveStatus() {
            // Given
            createSecuserroleWithActive("P900", "doctor", "ORG1", 1);
            createSecuserroleWithActive("P901", "nurse", "ORG1", 0);
            entityManager.flush();

            // When
            List<Secuserrole> activeResults = secuserroleDao.findByActiveyn(1);

            // Then
            assertThat(activeResults)
                .isNotEmpty()
                .allMatch(r -> r.getActiveyn() != null && r.getActiveyn().equals(1));
        }

        @Test
        @Tag("read")
        @DisplayName("should find all secuserroles")
        void shouldFindAll() {
            // Given
            createSecuserrole("P111", "doctor", "ORG1");
            createSecuserrole("P222", "nurse", "ORG2");
            entityManager.flush();

            // When
            List<Secuserrole> results = secuserroleDao.findAll();

            // Then
            assertThat(results).isNotEmpty();
        }
    }

    /** Tests for delete operations with single parameters. */
    @Nested
    @DisplayName("Delete operations")
    class DeleteOperations {

        @Test
        @Tag("delete")
        @DisplayName("should delete by orgcd")
        void shouldDeleteByOrgcd() {
            // Given
            createSecuserrole("P001", "doctor", "ORG1");
            createSecuserrole("P002", "nurse", "ORG1");
            createSecuserrole("P003", "admin", "ORG2");
            entityManager.flush();

            // When
            int deleted = secuserroleDao.deleteByOrgcd("ORG1");

            // Then
            assertThat(deleted).isEqualTo(2);
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete by provider number")
        void shouldDeleteByProviderNo() {
            // Given
            createSecuserrole("PDEL1", "doctor", "ORG1");
            createSecuserrole("PDEL1", "nurse", "ORG2");
            createSecuserrole("PDEL2", "admin", "ORG1");
            entityManager.flush();

            // When
            int deleted = secuserroleDao.deleteByProviderNo("PDEL1");

            // Then
            assertThat(deleted).isEqualTo(2);
        }
    }
}
