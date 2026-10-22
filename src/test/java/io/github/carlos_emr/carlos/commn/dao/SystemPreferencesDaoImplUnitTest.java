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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Deterministic tests for the COUNT fallback in {@link SystemPreferencesDaoImpl#upsertPreference}:
 * when the UPDATE reports 0 rows (some JDBC configurations count changed, not matched, rows, so an
 * identical re-save reports 0) the DAO inserts only if a count finds no row.
 *
 * @since 2026-10-01
 */
@Tag("unit")
@Tag("dao")
@DisplayName("SystemPreferencesDaoImpl upsertPreference COUNT fallback")
class SystemPreferencesDaoImplUnitTest {

    private static final LAB_DISPLAY_PREFERENCE_KEYS KEY = LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_max_size;

    private EntityManager entityManager;
    private Query update;
    private TypedQuery<Long> count;
    private SystemPreferencesDaoImpl dao;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityManager = mock(EntityManager.class);
        update = mock(Query.class);
        count = mock(TypedQuery.class);
        when(entityManager.createQuery(org.mockito.ArgumentMatchers.startsWith("UPDATE SystemPreferences")))
                .thenReturn(update);
        when(update.setParameter(anyInt(), any())).thenReturn(update);
        when(entityManager.createQuery(org.mockito.ArgumentMatchers.startsWith("SELECT COUNT"), eq(Long.class)))
                .thenReturn(count);
        when(count.setParameter(anyInt(), any())).thenReturn(count);
        dao = new SystemPreferencesDaoImpl();
        dao.entityManager = entityManager;
    }

    @Test
    @DisplayName("should not insert when the update reports 0 rows but a row with the name exists")
    void shouldNotInsert_whenUpdateReportsZeroButCountFindsRow() {
        when(update.executeUpdate()).thenReturn(0);
        when(count.getSingleResult()).thenReturn(1L);

        dao.upsertPreference(KEY, "1048576");

        verify(count).setParameter(1, KEY.name());
        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("should insert exactly one row when the update reports 0 rows and the count is 0")
    void shouldInsertOneRow_whenUpdateAndCountFindNothing() {
        when(update.executeUpdate()).thenReturn(0);
        when(count.getSingleResult()).thenReturn(0L);

        dao.upsertPreference(KEY, "1048576");

        ArgumentCaptor<SystemPreferences> inserted = ArgumentCaptor.forClass(SystemPreferences.class);
        verify(entityManager).persist(inserted.capture());
        assertThat(inserted.getValue().getName()).isEqualTo(KEY.name());
        assertThat(inserted.getValue().getValue()).isEqualTo("1048576");
        assertThat(inserted.getValue().getUpdateDate()).isNotNull();
    }

    @Test
    @DisplayName("should neither count nor insert when the update matched a row")
    void shouldSkipCountAndInsert_whenUpdateMatchedRows() {
        when(update.executeUpdate()).thenReturn(2);

        dao.upsertPreference(KEY, "1048576");

        verify(update).setParameter(1, "1048576");
        verify(update).setParameter(3, KEY.name());
        verify(entityManager, never()).createQuery(anyString(), eq(Long.class));
        verify(entityManager, never()).persist(any());
    }
}
