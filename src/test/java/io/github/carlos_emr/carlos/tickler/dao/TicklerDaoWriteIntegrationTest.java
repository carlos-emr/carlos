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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

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
    @DisplayName("should hand the edit a locked tickler that was added outside a program")
    void shouldLoadTheLockedTicklerForEdit_whenItsProgramIsZero() {
        // The Add Tickler form stores program_id 0 when the user is in no program, and no program
        // row has that id. The production schema has no foreign key there; the generated test
        // schema does, so the value is stored with the key check off for that one statement.
        Tickler tickler = createTickler(5004, "Added outside a program", Tickler.STATUS.A);
        entityManager.flush();
        storeProgramZero(tickler.getId());
        entityManager.clear();
        var manager = new io.github.carlos_emr.carlos.managers.TicklerManagerImpl();
        var security = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class,
                call -> call.getMethod().getReturnType() == boolean.class ? Boolean.TRUE : null);
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "ticklerDao", ticklerDao);
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "securityInfoManager", security);
        var user = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.utility.LoggedInInfo.class);
        org.mockito.Mockito.when(user.getLoggedInProviderNo()).thenReturn("999998");

        // Edit Tickler answered HTTP 500 here: after the locked load, the detail query's join fetch
        // of the program tried to fill the reference to program 0 from an all-NULL row.
        Tickler forEdit = manager.getTicklerForUpdate(user, tickler.getId());

        assertThat(forEdit.getProgramId()).isZero();
        assertThat(forEdit.getMessage()).isEqualTo("Added outside a program");
        assertThat(org.hibernate.Hibernate.isInitialized(forEdit.getComments())).isTrue();
    }

    /**
     * Stores program_id 0, which the generated test schema's foreign key would refuse.
     *
     * <p>H2's switch applies to the whole database and commits the connection it runs on. On this
     * test's connection it would commit the test's rows into the database that every later test
     * in the same fork reads, so it runs in a transaction of its own and this test's transaction
     * stays open, to be rolled back as usual. MariaDB's switch belongs to the session and commits
     * nothing, so there it has to run on this test's connection.</p>
     */
    private void storeProgramZero(Integer ticklerNo) {
        boolean h2 = entityManager.unwrap(org.hibernate.Session.class).doReturningWork(
                connection -> connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("h2"));
        setForeignKeyChecks(h2, false);
        try {
            entityManager.createNativeQuery("UPDATE tickler SET program_id = 0 WHERE tickler_no = ?1")
                    .setParameter(1, ticklerNo).executeUpdate();
        } finally {
            setForeignKeyChecks(h2, true);
        }
        // Had the switch committed this test's transaction, another transaction would see its row.
        assertThat(countInItsOwnTransaction(ticklerNo))
                .as("the tickler this test stored must stay uncommitted").isZero();
    }

    private void setForeignKeyChecks(boolean h2, boolean checked) {
        if (!h2) {
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS=" + (checked ? 1 : 0)).executeUpdate();
            return;
        }
        inItsOwnTransaction().executeWithoutResult(status -> entityManager
                .createNativeQuery("SET REFERENTIAL_INTEGRITY " + (checked ? "TRUE" : "FALSE")).executeUpdate());
    }

    private long countInItsOwnTransaction(Integer ticklerNo) {
        Number count = inItsOwnTransaction().execute(status -> (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM tickler WHERE tickler_no = ?1")
                .setParameter(1, ticklerNo).getSingleResult());
        return count.longValue();
    }

    private org.springframework.transaction.support.TransactionTemplate inItsOwnTransaction() {
        var separate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        separate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return separate;
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
