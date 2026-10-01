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
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link LabPdfPreviewSettingsService}.
 *
 * @since 2026-09-30
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
@DisplayName("LabPdfPreviewSettingsService")
class LabPdfPreviewSettingsServiceUnitTest {

    private SystemPreferencesDao dao;
    private LabPdfPreviewSettingsService service;

    @BeforeEach
    void setUp() {
        dao = mock(SystemPreferencesDao.class);
        service = new LabPdfPreviewSettingsService(dao);
    }

    @Test
    @DisplayName("should return defaults when no preference rows exist")
    void shouldReturnDefaults_whenNoRowsExist() {
        assertThat(service.load()).isEqualTo(LabPdfPreviewSettings.DEFAULTS);
    }

    @Test
    @DisplayName("should read stored preference rows")
    void shouldReadStoredValues_whenRowsExist() {
        when(dao.findPreferenceByName(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_inline_preview))
                .thenReturn(new SystemPreferences("lab_pdf_inline_preview", "false"));
        when(dao.findPreferenceByName(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_max_size))
                .thenReturn(new SystemPreferences("lab_pdf_max_size", "5MB"));

        assertThat(service.load()).isEqualTo(new LabPdfPreviewSettings(false, 5L * 1024 * 1024));
    }

    @Test
    @DisplayName("should treat a NULL preference value like a missing row")
    void shouldUseDefaults_whenStoredValueIsNull() {
        when(dao.findPreferenceByName(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_inline_preview))
                .thenReturn(new SystemPreferences("lab_pdf_inline_preview", null));

        assertThat(service.load().inlinePreviewEnabled()).isTrue();
    }

    @Test
    @DisplayName("should insert rows that do not exist yet")
    void shouldPersistNewRows_whenSavingFirstTime() {
        service.save(new LabPdfPreviewSettings(false, 2L * 1024 * 1024));

        ArgumentCaptor<SystemPreferences> saved = ArgumentCaptor.forClass(SystemPreferences.class);
        verify(dao, org.mockito.Mockito.times(2)).persist(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(SystemPreferences::getName, SystemPreferences::getValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("lab_pdf_inline_preview", "false"),
                        org.assertj.core.groups.Tuple.tuple("lab_pdf_max_size", "2097152"));
        verify(dao, never()).merge(any());
    }

    @Test
    @DisplayName("should update rows that already exist")
    void shouldMergeExistingRows_whenSaving() {
        SystemPreferences preview = new SystemPreferences("lab_pdf_inline_preview", "false");
        SystemPreferences size = new SystemPreferences("lab_pdf_max_size", "1");
        // AbstractModel.equals needs an id; stored rows always have one.
        preview.setId(1);
        size.setId(2);
        when(dao.findPreferenceByName(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_inline_preview)).thenReturn(preview);
        when(dao.findPreferenceByName(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_max_size)).thenReturn(size);

        service.save(new LabPdfPreviewSettings(true, 1024));

        assertThat(preview.getValue()).isEqualTo("true");
        assertThat(size.getValue()).isEqualTo("1024");
        verify(dao).merge(preview);
        verify(dao).merge(size);
        verify(dao, never()).persist(any());
    }
}
