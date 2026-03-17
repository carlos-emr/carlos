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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingNotes;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Integration tests for {@link BillingNoteDao} covering find operations.
 *
 * <p>Migrated from legacy {@code BillingNotesDaoTest} (JUnit 4 / DaoTestFixtures)
 * with strengthened assertions and proper data setup.</p>
 *
 * @since 2026-03-07
 * @see BillingNoteDao
 */
@DisplayName("BillingNoteDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class BillingNotesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingNoteDao dao;

    private BillingNotes createAndPersist(int billingMasterNo, int noteType, String note) {
        BillingNotes entity = new BillingNotes(billingMasterNo, new Date(), "999999", note, noteType);
        dao.persist(entity);
        hibernateTemplate.flush();
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist billing note and generate ID")
    void shouldPersistNote_withGeneratedId() {
        BillingNotes entity = createAndPersist(100, 2, "Test note");

        assertThat(entity.getId()).isNotNull().isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find notes by billing master number and note type")
    void shouldReturnNotes_byBillingMasterNoAndNoteType() {
        int masterNo = 5001;
        int noteType = 2;
        createAndPersist(masterNo, noteType, "Note A");
        createAndPersist(masterNo, noteType, "Note B");
        createAndPersist(masterNo, 3, "Different type note");

        List<BillingNotes> results = dao.findNotes(masterNo, noteType);

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(n -> {
            assertThat(n.getBillingmasterNo()).isEqualTo(masterNo);
            assertThat(n.getNoteType()).isEqualTo(noteType);
        });
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no notes match")
    void shouldReturnEmptyList_whenNoNotesMatch() {
        List<BillingNotes> results = dao.findNotes(99999, 99);

        assertThat(results).isNotNull().isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find single note by billing master number and note type")
    void shouldReturnSingleNote_byBillingMasterNoAndNoteType() {
        int masterNo = 5002;
        int noteType = 5;
        BillingNotes saved = createAndPersist(masterNo, noteType, "Single note");

        BillingNotes found = dao.findSingleNote(masterNo, noteType);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getNote()).isEqualTo("Single note");
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when single note not found")
    void shouldReturnNull_whenSingleNoteNotFound() {
        BillingNotes found = dao.findSingleNote(99998, 99);

        assertThat(found).isNull();
    }
}
