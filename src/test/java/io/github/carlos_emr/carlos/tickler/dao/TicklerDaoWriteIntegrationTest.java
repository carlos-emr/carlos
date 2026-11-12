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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for TicklerDao write operations.
 *
 * <p>This test class validates all write operations including create, update,
 * and delete operations. These tests verify that data modifications are
 * correctly persisted to the database.</p>
 *
 * <p><b>Future Expansion:</b></p>
 * <ul>
 *   <li>Delete operations (soft delete, hard delete)</li>
 *   <li>Batch operations (bulk create, bulk update)</li>
 *   <li>Cascade operations</li>
 *   <li>Concurrent modification handling</li>
 *   <li>Validation and constraint testing</li>
 * </ul>
 *
 * @since 2025-01-17
 * @see TicklerDao
 * @see TicklerDaoBaseIntegrationTest
 */
@DisplayName("Tickler DAO Write Integration Tests")
@Tag("integration")
@Tag("database")
@Tag("slow")
public class TicklerDaoWriteIntegrationTest extends TicklerDaoBaseIntegrationTest {

    @Test
    @DisplayName("should persist new history and comments during an intervening authorization query flush")
    void shouldPersistNewChildren_whenQueryFlushesManagedTickler() {
        Tickler tickler = createTickler(5001, "Atomic edit", Tickler.STATUS.A);
        entityManager.flush();
        var update = new io.github.carlos_emr.carlos.commn.model.TicklerUpdate();
        update.setTicklerNo(tickler.getId());
        update.setProviderNo("999998");
        update.setUpdateDate(new java.util.Date());
        update.setStatus(Tickler.STATUS.A);
        tickler.getUpdates().add(update);
        var comment = new io.github.carlos_emr.carlos.commn.model.TicklerComment();
        comment.setTicklerNo(tickler.getId());
        comment.setProviderNo("999998");
        comment.setMessage("Atomic comment");
        comment.setUpdateDate(new java.util.Date());
        tickler.getComments().add(comment);

        // Security lookups can auto-flush all pending changes before the manager explicitly
        // persists its children. Both children belong to the aggregate and must be persistable.
        entityManager.createNativeQuery("SELECT COUNT(*) FROM secUserRole", Long.class).getSingleResult();
        entityManager.flush();
        entityManager.clear();
        Tickler loaded = ticklerDao.find(tickler.getId());
        assertThat(loaded.getUpdates()).hasSize(1);
        assertThat(loaded.getComments()).extracting("message").containsExactly("Atomic comment");
    }

    @Test
    @DisplayName("should read the stored row again under the lock when the tickler was already loaded")
    void shouldReturnStoredState_whenLockingAnAlreadyLoadedTickler() {
        Tickler tickler = createTickler(5002, "Stale list", Tickler.STATUS.A);
        entityManager.flush();
        // Another writer changes the row after this persistence context loaded it.
        entityManager.createNativeQuery("UPDATE tickler SET status = 'D' WHERE tickler_no = ?1")
                .setParameter(1, tickler.getId()).executeUpdate();
        assertThat(tickler.getStatus()).isEqualTo(Tickler.STATUS.A);

        Tickler locked = ticklerDao.findForUpdate(tickler.getId());

        assertThat(locked).isSameAs(tickler);
        assertThat(locked.getStatus()).isEqualTo(Tickler.STATUS.D);
        // The fetch-joined detail read used by the edit path still resolves to the locked instance.
        assertThat(ticklerDao.find(tickler.getId())).isSameAs(locked);
        assertThat(ticklerDao.find(tickler.getId()).getStatus()).isEqualTo(Tickler.STATUS.D);
    }

    @Test
    @DisplayName("should load the comments and history on the locked tickler itself")
    void shouldLoadCommentsAndHistory_onTheLockedTickler() {
        Tickler tickler = createTickler(5003, "Edited under the lock", Tickler.STATUS.A);
        entityManager.flush();
        entityManager.createNativeQuery(
                "INSERT INTO tickler_comments (tickler_no, message, provider_no, update_date) VALUES (?1, 'First comment', '999998', ?2)")
                .setParameter(1, tickler.getId()).setParameter(2, new java.util.Date()).executeUpdate();
        entityManager.clear();

        Tickler locked = ticklerDao.findForUpdate(tickler.getId());

        // Everything the Edit Tickler save reads is on the locked instance, so its caller needs no
        // later detail query. It must not run one: find() join-fetches the program, and for a
        // tickler added outside a program (program_id 0, which names no row and which the generated
        // test schema's foreign key does not let a test store) Hibernate then fails filling the
        // program reference that the locked load created.
        assertThat(org.hibernate.Hibernate.isInitialized(locked.getComments())).isTrue();
        assertThat(org.hibernate.Hibernate.isInitialized(locked.getUpdates())).isTrue();
        assertThat(locked.getComments()).extracting("message").containsExactly("First comment");
    }

    @Test
    @DisplayName("should return null when locking a tickler that does not exist")
    void shouldReturnNull_whenLockingMissingTickler() {
        assertThat(ticklerDao.findForUpdate(Integer.MAX_VALUE)).isNull();
        assertThat(ticklerDao.findForUpdate(null)).isNull();
    }

    @Nested
    @DisplayName("Create and Update Operations")
    class CreateAndUpdateOperations {

        @Test
        @Tag("create")
        @Tag("update")
        @DisplayName("should persist new tickler and successfully merge updates")
        void shouldPersistNewTicklerAndMergeUpdates_whenValidDataProvided() {
            // Given
            ensureDemographicExists(5001);
            Tickler tickler = new Tickler();
            tickler.setDemographicNo(5001);
            tickler.setMessage("Persist test");
            tickler.setCreator("999998");
            tickler.setTaskAssignedTo("999998");
            tickler.setStatus(Tickler.STATUS.A);
            tickler.setServiceDate(new java.util.Date());

            // When - save
            entityManager.persist(tickler);
            entityManager.flush();
            Integer id = tickler.getId();
            assertThat(id).isNotNull();

            // When - merge (update)
            tickler.setMessage("Updated message");
            tickler.setPriority(Tickler.PRIORITY.High);
            entityManager.merge(tickler);
            entityManager.flush();
            entityManager.clear();

            // Then
            Tickler updated = ticklerDao.find(id);
            assertThat(updated.getMessage()).isEqualTo("Updated message");
            assertThat(updated.getPriority()).isEqualTo(Tickler.PRIORITY.High);
        }
    }
}
