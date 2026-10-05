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
