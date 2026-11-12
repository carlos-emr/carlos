/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package io.github.carlos_emr.carlos.tickler.dao;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for TicklerDao find operations.
 *
 * <p>This test class validates all find-related operations of the TicklerDao,
 * including finding by ID, finding active ticklers, and finding by various
 * criteria. These are simple retrieval operations that don't involve complex
 * queries or aggregations.</p>
 *
 * @since 2025-01-17
 * @see TicklerDao
 * @see TicklerDaoBaseIntegrationTest
 */
@DisplayName("Tickler DAO Find Integration Tests")
@Tag("integration")
@Tag("database")
@Tag("slow")
@Tag("read")
public class TicklerDaoFindIntegrationTest extends TicklerDaoBaseIntegrationTest {

    @Nested
    @DisplayName("Find by ID")
    class FindById {

        @Test
        @DisplayName("should return tickler when valid ID is provided")
        void shouldReturnTickler_whenValidIdProvided() {
            // Given: Create and persist a new tickler
            ensureDemographicExists(2001);
            Tickler newTickler = new Tickler();
            newTickler.setDemographicNo(2001);
            newTickler.setMessage("Find by ID test");
            newTickler.setCreator("999998");
            newTickler.setTaskAssignedTo("999998");
            newTickler.setStatus(Tickler.STATUS.A);
            newTickler.setServiceDate(new java.util.Date());
            entityManager.persist(newTickler);
            entityManager.flush();
            Integer id = newTickler.getId();
            entityManager.clear();

            // When
            Tickler found = ticklerDao.find(id);

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(id);
            assertThat(found.getMessage()).isEqualTo("Find by ID test");
            assertThat(found.getDemographicNo()).isEqualTo(2001);
            assertThat(Hibernate.isInitialized(found.getDemographic())).isTrue();
            assertThat(found.getDemographic().getDemographicNo()).isEqualTo(2001);
        }
    }

    @Nested
    @DisplayName("Find patient reference without loading clinical entities")
    class FindPatientReference {
        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void shouldPreserveRawDemographicFieldsAcrossAuditFlush_whenLookingUpOnlyThePatientReference(boolean nullable) {
            Tickler tickler = createTickler(2001, "Scalar authorization reference", Tickler.STATUS.A);
            entityManager.flush();
            Integer ticklerId = tickler.getId();
            entityManager.clear();
            // Property-based Demographic getters normalize these legacy NULLs to empty
            // strings. Materializing that entity before a refusal can dirty it at flush.
            entityManager.createNativeQuery("UPDATE demographic SET phone = :value, phone2 = :value, "
                            + "patient_status = :value, roster_status = :value, chart_no = :value, "
                            + "alias = :value, gender = :value, pronoun = :value WHERE demographic_no = :patient")
                    .setParameter("value", nullable ? null : " X")
                    .setParameter("patient", 2001).executeUpdate();
            entityManager.clear();
            List<Object> before = rawDemographicFields(2001);
            if (nullable) assertThat(before).allMatch(Objects::isNull);
            else assertThat(before).containsOnly(" X");
            assertThat(managedEntityCount()).isZero();

            assertThat(ticklerDao.findDemographicNos(ticklerId)).containsExactly(2001);

            assertThat(managedEntityCount()).as("authorization lookup must not hydrate the tickler or its patient").isZero();
            // The real synchronous audit writer persists a log and flushes the same
            // EntityManager even when subsequent chart authorization refuses access.
            OscarLog audit = new OscarLog();
            audit.setProviderNo("999998");
            audit.setAction("read");
            audit.setContent("tickler authorization reference");
            audit.setContentId(ticklerId.toString());
            audit.setDemographicId(2001);
            entityManager.persist(audit);
            entityManager.flush();

            assertThat(rawDemographicFields(2001)).containsExactlyElementsOf(before);
            assertThat(managedEntityCount()).as("only the audit row should be managed").isEqualTo(1);
            assertThat(entityManager.createNativeQuery("SELECT COUNT(*) FROM log WHERE id = :id", Long.class)
                    .setParameter("id", audit.getId()).getSingleResult()).isEqualTo(1L);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
        void shouldReturnNoPatientAndLoadNoEntities_whenTheTicklerIdDoesNotExist(Integer id) {
            entityManager.flush();
            entityManager.clear();

            assertThat(ticklerDao.findDemographicNos(id)).isEmpty();

            assertThat(managedEntityCount()).isZero();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {0})
        void shouldDistinguishAnExistingUnassignedTicklerFromAMissingRow_withoutLoadingItsPatient(Integer patient) {
            Tickler tickler = createTickler(2001, "Unassigned scalar authorization reference", Tickler.STATUS.A);
            entityManager.flush();
            Integer id = tickler.getId();
            // A zero patient is an invalid historical sentinel, retained in the projection
            // so the manager can audit the existing tickler before its caller refuses it.
            if (patient != null) ensureDemographicExists(patient);
            entityManager.createNativeQuery("UPDATE tickler SET demographic_no = :patient WHERE tickler_no = :id")
                    .setParameter("patient", patient).setParameter("id", id).executeUpdate();
            entityManager.clear();

            assertThat(ticklerDao.findDemographicNos(id)).containsExactly(patient);

            assertThat(managedEntityCount()).isZero();
        }

        @ParameterizedTest
        @EnumSource(value = Tickler.STATUS.class, names = {"C", "D"})
        void shouldRetainReferenceLookupForCompletedAndDeletedTicklers_withoutLoadingTheirPatients(Tickler.STATUS status) {
            Tickler tickler = createTickler(2001, "Historical scalar authorization reference", status);
            entityManager.flush();
            Integer id = tickler.getId();
            entityManager.clear();

            assertThat(ticklerDao.findDemographicNos(id)).containsExactly(2001);

            assertThat(managedEntityCount()).isZero();
        }

        private List<Object> rawDemographicFields(int patient) {
            Object[] values = (Object[]) entityManager.createNativeQuery("SELECT phone, phone2, patient_status, "
                            + "roster_status, chart_no, alias, gender, pronoun FROM demographic WHERE demographic_no = :patient")
                    .setParameter("patient", patient).getSingleResult();
            return Arrays.asList(values);
        }

        private int managedEntityCount() {
            return entityManager.unwrap(Session.class).getStatistics().getEntityCount();
        }
    }

    @Nested
    @DisplayName("Find Active Ticklers")
    class FindActiveTicklers {

        @Test
        @Tag("filter")
        @DisplayName("should return only active ticklers when multiple statuses exist")
        void shouldReturnOnlyActiveTicklers_whenSearchingByDemographic() {
            // Given: Create multiple ticklers with different statuses for same demographic
            Tickler active1 = createTickler(3001, "Active 1", Tickler.STATUS.A);
            Tickler active2 = createTickler(3001, "Active 2", Tickler.STATUS.A);
            Tickler completed = createTickler(3001, "Completed", Tickler.STATUS.C);
            entityManager.flush();

            // When
            List<Tickler> activeTicklers = ticklerDao.findActiveByDemographicNo(3001);

            // Then
            assertThat(activeTicklers).hasSize(2);
            assertThat(activeTicklers).allMatch(t -> t.getStatus() == Tickler.STATUS.A);
            assertThat(activeTicklers).allMatch(t -> t.getDemographicNo().equals(3001));
            assertThat(activeTicklers).extracting(Tickler::getMessage)
                .containsExactlyInAnyOrder("Active 1", "Active 2");
        }

        @Test
        @Tag("search")
        @DisplayName("should return active ticklers with matching message for specified patients")
        void shouldReturnActiveTicklersWithMatchingMessage_whenSearchingMultiplePatients() {
            // Given
            createTickler(4001, "Follow-up required", Tickler.STATUS.A);
            createTickler(4002, "Follow-up required", Tickler.STATUS.A);
            createTickler(4003, "Different message", Tickler.STATUS.A);
            createTickler(4001, "Follow-up required", Tickler.STATUS.C); // Completed, should not be found
            entityManager.flush();

            List<Integer> demoNos = Arrays.asList(4001, 4002, 4003);

            // When
            List<Tickler> results = ticklerDao.findActiveByMessageForPatients(demoNos, "Follow-up required");

            // Then
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(t -> t.getMessage().equals("Follow-up required"));
            assertThat(results).allMatch(t -> t.getStatus() == Tickler.STATUS.A);
            assertThat(results).extracting(Tickler::getDemographicNo)
                .containsExactlyInAnyOrder(4001, 4002);
        }

        @Test
        @Tag("search")
        @DisplayName("should return active ticklers matching both demographic and message")
        void shouldFindActiveTicklers_whenBothDemographicAndMessageMatch() {
            // Given
            createTickler(6001, "Specific message", Tickler.STATUS.A);
            createTickler(6001, "Different message", Tickler.STATUS.A);
            createTickler(6001, "Specific message", Tickler.STATUS.C); // Completed
            entityManager.flush();

            // When
            List<Tickler> results = ticklerDao.findActiveByDemographicNoAndMessage(6001, "Specific message");

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDemographicNo()).isEqualTo(6001);
            assertThat(results.get(0).getMessage()).isEqualTo("Specific message");
            assertThat(results.get(0).getStatus()).isEqualTo(Tickler.STATUS.A);
        }
    }

    @Nested
    @DisplayName("List Operations")
    class ListOperations {

        @Test
        @Tag("query")
        @DisplayName("should list all ticklers within date range for demographic")
        void shouldListTicklers_whenDateRangeSpecified() {
            // Given
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -10);
            Date tenDaysAgo = cal.getTime();

            cal.add(Calendar.DAY_OF_MONTH, 20);
            Date tenDaysFromNow = cal.getTime();

            // When
            List<Tickler> results = ticklerDao.listTicklers(1001, tenDaysAgo, tenDaysFromNow);

            // Then
            assertThat(results).isNotNull();
            if (!results.isEmpty()) {
                assertThat(results).allMatch(t -> t.getDemographicNo().equals(1001));
            }
        }
    }
}
