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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link IncomingLabRulesDao} covering persist, findByProviderNo,
 * findCurrentByProviderNo, findByProviderNoAndFrwdProvider, and findCurrentByProviderNoAndFrwdProvider.
 *
 * <p>Note: findRules is not tested because it requires a cross-entity join with Provider,
 * which is complex to set up in this test context.</p>
 *
 * <p>Migrated from legacy {@code IncomingLabRulesDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see IncomingLabRulesDao
 */
@DisplayName("IncomingLabRules Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class IncomingLabRulesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private IncomingLabRulesDao dao;

    /**
     * Helper to create and persist an IncomingLabRules entity.
     */
    private IncomingLabRules createRule(String providerNo, String frwdProviderNo, String archive) {
        IncomingLabRules entity = new IncomingLabRules();
        entity.setProviderNo(providerNo);
        entity.setFrwdProviderNo(frwdProviderNo);
        entity.setArchive(archive);
        entity.setStatus("A");
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist incoming lab rules with generated ID")
        void shouldPersistIncomingLabRules_whenValidDataProvided() {
            IncomingLabRules entity = new IncomingLabRules();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find incoming lab rule by ID with correct fields")
        void shouldFindIncomingLabRule_whenValidIdProvided() {
            IncomingLabRules saved = createRule("PR001", "FW001", "0");
            hibernateTemplate.flush();

            IncomingLabRules found = dao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getProviderNo()).isEqualTo("PR001");
            assertThat(found.getFrwdProviderNo()).isEqualTo("FW001");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find rules by provider number")
        void shouldFindRules_byProviderNo() {
            createRule("PR100", "FW100", "0");
            createRule("PR100", "FW200", "1");
            createRule("PR200", "FW300", "0");
            hibernateTemplate.flush();

            List<IncomingLabRules> result = dao.findByProviderNo("PR100");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r -> r.getProviderNo().equals("PR100"));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no rules exist for provider")
        void shouldReturnEmptyList_whenNoRulesExistForProvider() {
            List<IncomingLabRules> result = dao.findByProviderNo("NONEXIST");

            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find current (non-archived) rules by provider number")
        void shouldFindCurrentRules_byProviderNo() {
            createRule("PR300", "FW100", "0");
            createRule("PR300", "FW200", "1");
            createRule("PR300", "FW300", "0");
            hibernateTemplate.flush();

            List<IncomingLabRules> result = dao.findCurrentByProviderNo("PR300");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r -> r.getArchive().equals("0"));
        }

        @Test
        @Tag("query")
        @DisplayName("should find rules by provider number and forward provider")
        void shouldFindRules_byProviderNoAndFrwdProvider() {
            createRule("PR400", "FW400", "0");
            createRule("PR400", "FW400", "1");
            createRule("PR400", "FW500", "0");
            hibernateTemplate.flush();

            List<IncomingLabRules> result = dao.findByProviderNoAndFrwdProvider("PR400", "FW400");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r -> r.getFrwdProviderNo().equals("FW400"));
        }

        @Test
        @Tag("query")
        @DisplayName("should find current rules by provider and forward provider")
        void shouldFindCurrentRules_byProviderNoAndFrwdProvider() {
            createRule("PR500", "FW500", "0");
            createRule("PR500", "FW500", "1");
            createRule("PR500", "FW600", "0");
            hibernateTemplate.flush();

            List<IncomingLabRules> result = dao.findCurrentByProviderNoAndFrwdProvider("PR500", "FW500");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getArchive()).isEqualTo("0");
            assertThat(result.get(0).getFrwdProviderNo()).isEqualTo("FW500");
        }
    }
}
