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
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link UserAccessDao} validating HQL query correctness
 * and parameter binding for the Hibernate 5-to-6 migration.
 *
 * <p>The {@link UserAccessValue} entity is mapped to the {@code v_user_access} database
 * view with {@code mutable="false"} in the HBM XML. In the test environment, Hibernate's
 * {@code hbm2ddl.auto=create} creates this as a regular table in H2, allowing insert
 * operations via native SQL while preserving the read-only HQL query behavior.</p>
 *
 * <p>The entity uses a <b>composite primary key</b> consisting of:
 * <ul>
 *   <li>{@code functionCd} (mapped to {@code objectname} column) - the security object name</li>
 *   <li>{@code orgCd} (mapped to {@code orgcd} column) - the organization code</li>
 * </ul>
 * Additional mapped properties include {@code providerNo}, {@code privilege},
 * {@code orgCdcsv}, and {@code orgApplicable}.</p>
 *
 * <p><b>Test scope limitations:</b></p>
 * <ul>
 *   <li>{@code GetUserAccessList(providerNo, null)} - Tested. Simple HQL with one
 *       positional parameter and ORDER BY clause.</li>
 *   <li>{@code GetUserAccessList(providerNo, shelterId)} - Not tested. The shelter ID
 *       branch uses string concatenation which is a pre-existing SQL injection
 *       vulnerability, not a migration concern.</li>
 *   <li>{@code GetUserOrgAccessList} - Not tested. Requires the {@code LstOrgcd} entity
 *       (mapped to {@code lst_orgcd} table) which is not available in the test persistence
 *       context.</li>
 * </ul>
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
public class UserAccessDaoIntegrationTest extends CarlosTestBase {

    /** DAO under test, autowired from Spring test context bean {@code userAccessDao}. */
    @Autowired
    private UserAccessDao userAccessDao;

    /** JPA EntityManager for native SQL test data setup and verification. */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Native SQL INSERT for the {@code v_user_access} table. Uses positional parameters
     * (?1 through ?6) for safe parameterized insertion. Required because the entity
     * mapping declares {@code mutable="false"}, preventing Hibernate DML operations.
     */
    private static final String INSERT_SQL = "INSERT INTO v_user_access (objectname, orgcd, provider_no, privilege, orgCdcsv, orgapplicable) VALUES (?1, ?2, ?3, ?4, ?5, ?6)";

    /**
     * Inserts a single row into the {@code v_user_access} table using native SQL.
     *
     * <p>Native SQL is required because the {@code UserAccessValue} HBM mapping
     * declares {@code mutable="false"}, which prevents Hibernate from performing
     * insert, update, or delete operations on this entity.</p>
     *
     * @param functionCd    String the security object name (composite key part 1)
     * @param orgCd         String the organization code (composite key part 2)
     * @param providerNo    String the provider number to associate the access with
     * @param privilege      String the privilege level (e.g., "r" for read, "w" for write, "x" for execute)
     * @param orgCdcsv      String the comma-separated organization code tree for shelter filtering
     * @param orgApplicable boolean whether the organization is applicable for this access entry
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

    /**
     * Sets up test data with access records for two providers.
     *
     * <p>Provider P001 receives three access records spanning two functions and two
     * organizations, with varying privilege levels. Provider P002 receives a single
     * record to verify cross-provider query isolation.</p>
     */
    @BeforeEach
    void setUp() {
        // Provider P001: three access records across two functions and two organizations
        insertUserAccess("functionA", "ORG1", "P001", "r", "S1,", true);
        insertUserAccess("functionB", "ORG1", "P001", "w", "S1,", true);
        insertUserAccess("functionA", "ORG2", "P001", "x", "S2,", false);

        // Provider P002: one access record to verify provider isolation
        insertUserAccess("functionC", "ORG1", "P002", "r", "S1,", true);
    }

    /**
     * Verifies that {@code GetUserAccessList} returns all access records for a given
     * provider when no shelter ID filter is applied (null shelterId branch).
     *
     * <p>This exercises the HQL query:
     * {@code from UserAccessValue s where s.providerNo= ?0 order by s.functionCd, s.privilege desc, s.orgCd}</p>
     */
    @Test
    @Tag("read")
    @DisplayName("should return user access list when provider matches and no shelter ID")
    void shouldReturnUserAccessList_whenProviderNoMatchesAndNoShelterId() {
        // When - retrieve without shelter filter (null triggers the simpler HQL branch)
        List results = userAccessDao.GetUserAccessList("P001", null);

        // Then - P001 has exactly 3 access records
        assertThat(results)
            .isNotNull()
            .hasSize(3);

        // Verify all returned records belong to the correct provider
        assertThat(results)
            .allSatisfy(item -> {
                UserAccessValue uav = (UserAccessValue) item;
                assertThat(uav.getProviderNo()).isEqualTo("P001");
            });
    }

    /**
     * Verifies that {@code GetUserAccessList} returns an empty list (not null)
     * when the provider number does not match any access records in the database.
     */
    @Test
    @Tag("read")
    @DisplayName("should return empty list when provider number does not match")
    void shouldReturnEmptyList_whenProviderNoDoesNotMatch() {
        // When - query with a provider that has no access records
        List results = userAccessDao.GetUserAccessList("NONEXISTENT", null);

        // Then - should return empty, not null
        assertThat(results)
            .isNotNull()
            .isEmpty();
    }

    /**
     * Verifies that {@code GetUserAccessList} returns results in the correct ORDER BY
     * sequence: {@code functionCd ASC, privilege DESC, orgCd ASC}.
     *
     * <p>Test data for P001:
     * <pre>
     *   functionA | ORG1 | privilege=r
     *   functionA | ORG2 | privilege=x
     *   functionB | ORG1 | privilege=w
     * </pre>
     * Expected order after sorting:
     * <pre>
     *   functionA | x | ORG2  (functionA group, highest privilege first)
     *   functionA | r | ORG1  (functionA group, lower privilege)
     *   functionB | w | ORG1  (functionB group)
     * </pre>
     * </p>
     */
    @Test
    @Tag("read")
    @DisplayName("should return ordered results when multiple access records exist")
    void shouldReturnOrderedResults_whenMultipleAccessRecordsExist() {
        // When
        List results = userAccessDao.GetUserAccessList("P001", null);

        // Then - verify the total count
        assertThat(results).hasSize(3);

        UserAccessValue first = (UserAccessValue) results.get(0);
        UserAccessValue second = (UserAccessValue) results.get(1);
        UserAccessValue third = (UserAccessValue) results.get(2);

        // First entry: functionA group, privilege DESC means "x" comes before "r"
        assertThat(first.getFunctionCd()).isEqualTo("functionA");
        assertThat(first.getPrivilege()).isEqualTo("x");
        assertThat(first.getOrgCd()).isEqualTo("ORG2");

        // Second entry: still functionA, lower privilege
        assertThat(second.getFunctionCd()).isEqualTo("functionA");
        assertThat(second.getPrivilege()).isEqualTo("r");
        assertThat(second.getOrgCd()).isEqualTo("ORG1");

        // Third entry: functionB group (only one record)
        assertThat(third.getFunctionCd()).isEqualTo("functionB");
        assertThat(third.getPrivilege()).isEqualTo("w");
        assertThat(third.getOrgCd()).isEqualTo("ORG1");
    }
}
