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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderInboxItem;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProviderInboxRoutingDao} covering basic persistence
 * and provider inbox routing operations.
 *
 * <p>Migrated from legacy {@code ProviderInboxRoutingDaoTest} (JUnit 4 / DaoTestFixtures)
 * with BDD-style naming and AssertJ assertions.</p>
 *
 * @since 2026-03-07
 * @see ProviderInboxRoutingDao
 */
@DisplayName("ProviderInboxRoutingDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("inbox")
@Transactional
public class ProviderInboxRoutingDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderInboxRoutingDao dao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager em;

    @Test
    @Tag("create")
    @DisplayName("should persist provider inbox item with generated ID")
    void shouldPersistProviderInboxItem_whenValidDataProvided() throws Exception {
        // Given
        ProviderInboxItem entity = new ProviderInboxItem();
        EntityDataGenerator.generateTestDataForModelClass(entity);

        // When
        dao.persist(entity);
        hibernateTemplate.flush();

        // Then
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("create")
    @DisplayName("should not throw PersistenceException when adding to provider inbox")
    void shouldNotThrowPersistenceException_whenAddingToProviderInbox() {
        // PersistenceException indicates a JPA configuration problem.
        // The method may throw other exceptions due to missing prerequisite data,
        // but a PersistenceException specifically means the JPA mapping is broken.
        try {
            dao.addToProviderInbox("1", 1, LabResultData.DOCUMENT);
        } catch (PersistenceException e) {
            fail("PersistenceException indicates JPA configuration issue: " + e.getMessage());
        }
    }

    /**
     * Forwarding-rule scoping for inbox routing (issue #4120): a rule follows only the result
     * types it is scoped to, matching the lab and HRM routing paths.
     */
    @Nested
    @DisplayName("forwarding rules")
    @Tag("inbox")
    class ForwardingRules {
        private static final int DOCUMENT = 9841200;
        private static final String OWNER = "984121";
        private static final String FIRST = "984122";
        private static final String SECOND = "984123";

        private void provider(String providerNo) {
            Provider provider = new Provider(providerNo, "Synthetic", "doctor", "F", "", "Forwarding");
            provider.setStatus("1");
            em.persist(provider);
        }

        private IncomingLabRules rule(String from, String to, String status, String... types) {
            IncomingLabRules rule = new IncomingLabRules();
            rule.setProviderNo(from);
            rule.setFrwdProviderNo(to);
            rule.setStatus(status);
            for (String type : types) rule.addForwardType(type);
            em.persist(rule);
            em.flush();
            return rule;
        }

        private java.util.Map<String, String> inbox() {
            em.flush();
            java.util.Map<String, String> statuses = new java.util.TreeMap<>();
            for (ProviderInboxItem item : dao.getProvidersWithRoutingForDocument(LabResultData.DOCUMENT, DOCUMENT)) {
                statuses.put(item.getProviderNo(), item.getStatus());
            }
            return statuses;
        }

        @ParameterizedTest
        @ValueSource(strings = {"HL7", "HRM"})
        @DisplayName("should neither forward nor file a document when the rule is scoped to another type")
        void shouldNotForwardOrFileDocument_whenRuleIsScopedToAnotherType(String type) {
            provider(FIRST);
            rule(OWNER, FIRST, "F", type);

            dao.addToProviderInboxStrict(OWNER, DOCUMENT, LabResultData.DOCUMENT);

            assertThat(inbox()).containsExactly(entry(OWNER, ProviderInboxItem.NEW));
        }

        @Test
        @DisplayName("should forward and file a document when the rule includes DOC")
        void shouldForwardAndFileDocument_whenRuleIncludesDoc() {
            provider(FIRST);
            rule(OWNER, FIRST, "F", "HL7", "DOC");

            dao.addToProviderInboxStrict(OWNER, DOCUMENT, LabResultData.DOCUMENT);

            assertThat(inbox()).containsOnly(entry(OWNER, ProviderInboxItem.FILE), entry(FIRST, ProviderInboxItem.NEW));
        }

        @Test
        @DisplayName("should forward a document when a legacy rule has no type rows")
        void shouldForwardDocument_whenRuleHasNoTypeRows() {
            provider(FIRST);
            rule(OWNER, FIRST, "N");

            dao.addToProviderInboxStrict(OWNER, DOCUMENT, LabResultData.DOCUMENT);

            assertThat(inbox()).containsOnly(entry(OWNER, ProviderInboxItem.NEW), entry(FIRST, ProviderInboxItem.NEW));
        }

        @Test
        @DisplayName("should follow a chain only through rules that apply to documents")
        void shouldFollowChain_throughDocumentRulesOnly() {
            provider(FIRST);
            provider(SECOND);
            provider("984124");
            rule(OWNER, FIRST, "N", "DOC");
            rule(FIRST, SECOND, "N");
            rule(SECOND, "984124", "N", "HRM");
            // A cycle back to the owner terminates instead of re-routing.
            rule(SECOND, OWNER, "N", "DOC");

            dao.addToProviderInboxStrict(OWNER, DOCUMENT, LabResultData.DOCUMENT);

            assertThat(inbox().keySet()).containsExactly(OWNER, FIRST, SECOND);
        }

        @Test
        @DisplayName("should not continue a chain past a rule scoped to another type")
        void shouldNotContinueChain_whenIntermediateRuleIsScopedToAnotherType() {
            provider(FIRST);
            provider(SECOND);
            rule(OWNER, FIRST, "N", "HL7");
            rule(FIRST, SECOND, "N", "DOC");

            dao.addToProviderInboxStrict(OWNER, DOCUMENT, LabResultData.DOCUMENT);

            assertThat(inbox().keySet()).containsExactly(OWNER);
        }

        @ParameterizedTest
        @ValueSource(strings = {"1", "2"})
        @DisplayName("should ignore a rule that is not active, as HRM routing does")
        void shouldIgnoreRule_whenArchiveFlagIsNotZero(String archive) {
            provider(FIRST);
            IncomingLabRules rule = rule(OWNER, FIRST, "F", "DOC");
            rule.setArchive(archive);
            em.flush();

            dao.addToProviderInboxStrict(OWNER, DOCUMENT, LabResultData.DOCUMENT);

            assertThat(inbox()).containsExactly(entry(OWNER, ProviderInboxItem.NEW));
        }
    }
}
