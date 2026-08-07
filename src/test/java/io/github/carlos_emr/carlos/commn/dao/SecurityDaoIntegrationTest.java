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
import io.github.carlos_emr.carlos.commn.model.Security;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SecurityDao} covering provider-based lookups,
 * username queries, SSO key operations, and ordered result sets.
 *
 * <p>Migrated from legacy {@code SecurityDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage for LIKE queries and edge cases.</p>
 *
 * @since 2026-03-07
 * @see SecurityDao
 */
@DisplayName("SecurityDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecurityDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SecurityDao securityDao;

    private Security createSecurity(String providerNo, String userName, String password) {
        Security sec = new Security();
        sec.setProviderNo(providerNo);
        sec.setUserName(userName);
        sec.setPassword(password);
        sec.setLastUpdateDate(new Date());
        securityDao.persist(sec);
        return sec;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist security record with generated ID")
        void shouldPersistSecurityRecord_whenValidDataProvided() {
            Security sec = createSecurity("100001", "testuser1", "hashed_pw");
            assertThat(sec.getSecurityNo()).isPositive();
            assertThat(sec.getUserName()).isEqualTo("testuser1");
            assertThat(sec.getProviderNo()).isEqualTo("100001");
        }

        @Test
        @Tag("read")
        @DisplayName("should find security record by ID")
        void shouldFindSecurityRecord_whenValidIdProvided() {
            Security saved = createSecurity("100002", "testuser2", "hashed_pw");
            Security found = securityDao.find(saved.getSecurityNo());
            assertThat(found).isNotNull();
            assertThat(found.getUserName()).isEqualTo("testuser2");
        }
    }

    @Nested
    @DisplayName("findByProviderNo")
    class FindByProviderNo {

        @BeforeEach
        void setUp() {
            createSecurity("200001", "doc1", "pw1");
            createSecurity("200001", "doc1alt", "pw2");
            createSecurity("200002", "doc2", "pw3");
        }

        @Test
        @Tag("query")
        @DisplayName("should find security records by exact provider number")
        void shouldFindRecords_byExactProviderNo() {
            List<Security> results = securityDao.findByProviderNo("200001");
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(s -> "200001".equals(s.getProviderNo()));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for non-existent provider")
        void shouldReturnEmpty_forNonExistentProvider() {
            List<Security> results = securityDao.findByProviderNo("NONEXISTENT");
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserName")
    class FindByUserName {

        @BeforeEach
        void setUp() {
            createSecurity("300001", "uniqueuser", "pw1");
            createSecurity("300002", "anotheruser", "pw2");
        }

        @Test
        @Tag("query")
        @DisplayName("should find security record by exact username")
        void shouldFindRecord_byExactUsername() {
            List<Security> results = securityDao.findByUserName("uniqueuser");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProviderNo()).isEqualTo("300001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for non-existent username")
        void shouldReturnEmpty_forNonExistentUsername() {
            List<Security> results = securityDao.findByUserName("nobody");
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByLikeProviderNo (LIKE query)")
    class FindByLikeProviderNo {

        @BeforeEach
        void setUp() {
            createSecurity("400001", "user1", "pw1");
            createSecurity("400002", "user2", "pw2");
            createSecurity("500001", "user3", "pw3");
        }

        @Test
        @Tag("search")
        @DisplayName("should find records matching provider number pattern")
        void shouldFindRecords_matchingProviderPattern() {
            List<Security> results = securityDao.findByLikeProviderNo("4000%");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("search")
        @DisplayName("should return empty for non-matching pattern")
        void shouldReturnEmpty_forNonMatchingPattern() {
            List<Security> results = securityDao.findByLikeProviderNo("9999%");
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByLikeUserName (LIKE query)")
    class FindByLikeUserName {

        @BeforeEach
        void setUp() {
            createSecurity("600001", "admin_main", "pw1");
            createSecurity("600002", "admin_backup", "pw2");
            createSecurity("600003", "doctor_smith", "pw3");
        }

        @Test
        @Tag("search")
        @DisplayName("should find records matching username pattern")
        void shouldFindRecords_matchingUsernamePattern() {
            List<Security> results = securityDao.findByLikeUserName("admin%");
            assertThat(results).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByOneIdKey (SSO key)")
    class FindByOneIdKey {

        @Test
        @Tag("query")
        @DisplayName("should find record by SSO key")
        void shouldFindRecord_bySsoKey() {
            Security sec = createSecurity("700001", "ssouser", "pw1");
            sec.setOneIdKey("SSO-KEY-12345");
            securityDao.merge(sec);
            hibernateTemplate.flush();

            List<Security> results = securityDao.findByOneIdKey("SSO-KEY-12345");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getUserName()).isEqualTo("ssouser");
            assertThat(results.get(0).getProviderNo()).isEqualTo("700001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for non-existent SSO key")
        void shouldReturnEmpty_forNonExistentSsoKey() {
            List<Security> results = securityDao.findByOneIdKey("NONEXISTENT-KEY");
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllOrderBy")
    class FindAllOrderBy {

        @Test
        @Tag("query")
        @DisplayName("should return all records ordered by specified column")
        void shouldReturnAllRecords_orderedByColumn() {
            createSecurity("800001", "zuser", "pw1");
            createSecurity("800002", "auser", "pw2");

            List<Security> results = securityDao.findAllOrderBy("userName");
            assertThat(results).hasSize(2);
            assertThat(results.get(0).getUserName()).isEqualTo("auser");
            assertThat(results.get(1).getUserName()).isEqualTo("zuser");
        }
    }
}
