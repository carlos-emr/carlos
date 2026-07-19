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
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SecuserroleDao (security module).
 *
 * <p>These tests validate HQL queries with positional parameters.</p>
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

    @Nested
    @DisplayName("Single parameter queries")
    class SingleParamQueries {

        @Test
        @Tag("delete")
        @DisplayName("should delete by orgcd")
        void shouldDeleteByOrgcd() {
            // Given
            createSecuserrole("P001", "doctor", "ORG1");
            createSecuserrole("P002", "nurse", "ORG1");
            createSecuserrole("P003", "admin", "ORG2");  // Different org
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
            createSecuserrole("PDEL2", "admin", "ORG1");  // Different provider
            entityManager.flush();

            // When
            int deleted = secuserroleDao.deleteByProviderNo("PDEL1");

            // Then
            assertThat(deleted).isEqualTo(2);
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
}
