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
package io.github.carlos_emr.carlos.daos.security;

import io.github.carlos_emr.carlos.model.security.UserAccessValue;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link UserAccessDao} Hibernate migration validation.
 *
 * <p>These tests validate that HQL queries with positional parameters in
 * {@code UserAccessDaoImpl} bind parameters correctly after Hibernate migration.
 * The {@code UserAccessValue} entity is mapped as {@code mutable="false"} to the
 * {@code v_user_access} view, so test data must be inserted via native SQL.</p>
 *
 * <p><b>Note on GetUserOrgAccessList:</b> Tests for {@code GetUserOrgAccessList}
 * are intentionally omitted because that method joins against the {@code LstOrgcd}
 * entity (mapped to {@code lst_orgcd} table), which is not available in the test
 * context. The {@code GetUserAccessList} method queries only the
 * {@code UserAccessValue} entity and can be tested in isolation.</p>
 *
 * @since 2026-02-09
 * @see UserAccessDao
 * @see UserAccessValue
 */
@DisplayName("UserAccessDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class UserAccessDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private UserAccessDao userAccessDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String INSERT_SQL = "INSERT INTO v_user_access (objectname, orgcd, provider_no, privilege, orgCdcsv, orgapplicable) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";

    /**
     * Insert a row into the {@code v_user_access} table using native SQL.
     *
     * <p>Native SQL is required because the {@code UserAccessValue} HBM mapping
     * declares {@code mutable="false"}, which prevents Hibernate from performing
     * insert/update/delete operations on the entity.</p>
     *
     * @param functionCd  the security object name (composite key part 1)
     * @param orgCd       the organization code (composite key part 2)
     * @param providerNo  the provider number
     * @param privilege    the privilege level (e.g. "r", "w", "x")
     * @param orgCdcsv    the comma-separated organization code tree
     * @param orgApplicable whether the organization is applicable
     */
    private void insertUserAccess(String functionCd, String orgCd, String providerNo,
                                  String privilege, String orgCdcsv, boolean orgApplicable) {
        entityManager.createNativeQuery(INSERT_SQL)
            .setParameter(1, functionCd)
            .setParameter(2, orgCd)
            .setParameter(3, providerNo)
            .setParameter(4, privilege)
            .setParameter(5, orgCdcsv)
            .setParameter(6, orgApplicable)
            .executeUpdate();
        entityManager.flush();
    }

    @BeforeEach
    void setUp() {
        // Insert test data for provider P001
        insertUserAccess("functionA", "ORG1", "P001", "r", "S1,", true);
        insertUserAccess("functionB", "ORG1", "P001", "w", "S1,", true);
        insertUserAccess("functionA", "ORG2", "P001", "x", "S2,", false);

        // Insert test data for a different provider P002
        insertUserAccess("functionC", "ORG1", "P002", "r", "S1,", true);
    }

    @Test
    @Tag("read")
    @DisplayName("should return user access list when provider matches and no shelter ID")
    void shouldReturnUserAccessList_whenProviderNoMatchesAndNoShelterId() {
        // When
        List results = userAccessDao.GetUserAccessList("P001", null);

        // Then
        assertThat(results)
            .isNotNull()
            .hasSize(3);

        // Verify the results contain UserAccessValue instances for the correct provider
        assertThat(results)
            .allSatisfy(item -> {
                UserAccessValue uav = (UserAccessValue) item;
                assertThat(uav.getProviderNo()).isEqualTo("P001");
            });
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when provider number does not match")
    void shouldReturnEmptyList_whenProviderNoDoesNotMatch() {
        // When
        List results = userAccessDao.GetUserAccessList("NONEXISTENT", null);

        // Then
        assertThat(results)
            .isNotNull()
            .isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return ordered results when multiple access records exist")
    void shouldReturnOrderedResults_whenMultipleAccessRecordsExist() {
        // The query orders by: functionCd ASC, privilege DESC, orgCd ASC.
        // P001 has:
        //   functionA, ORG1, privilege=r
        //   functionA, ORG2, privilege=x
        //   functionB, ORG1, privilege=w
        // Expected order by (functionCd, privilege desc, orgCd):
        //   functionA, x, ORG2
        //   functionA, r, ORG1
        //   functionB, w, ORG1

        // When
        List results = userAccessDao.GetUserAccessList("P001", null);

        // Then
        assertThat(results).hasSize(3);

        UserAccessValue first = (UserAccessValue) results.get(0);
        UserAccessValue second = (UserAccessValue) results.get(1);
        UserAccessValue third = (UserAccessValue) results.get(2);

        // First group: functionA, ordered by privilege desc
        assertThat(first.getFunctionCd()).isEqualTo("functionA");
        assertThat(first.getPrivilege()).isEqualTo("x");
        assertThat(first.getOrgCd()).isEqualTo("ORG2");

        assertThat(second.getFunctionCd()).isEqualTo("functionA");
        assertThat(second.getPrivilege()).isEqualTo("r");
        assertThat(second.getOrgCd()).isEqualTo("ORG1");

        // Second group: functionB
        assertThat(third.getFunctionCd()).isEqualTo("functionB");
        assertThat(third.getPrivilege()).isEqualTo("w");
        assertThat(third.getOrgCd()).isEqualTo("ORG1");
    }
}
