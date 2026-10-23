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
package io.github.carlos_emr.carlos.chartspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences.ECHART_PREFERENCE_KEYS;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.Method;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unit tests for {@link ChartSpaceLayoutService}.
 *
 * @since 2026-10-23
 */
@Tag("unit")
@Tag("fast")
@DisplayName("ChartSpaceLayoutService")
class ChartSpaceLayoutServiceUnitTest {

    private static final ECHART_PREFERENCE_KEYS KEY = ECHART_PREFERENCE_KEYS.chartspace_default_layout;
    private static final String FAKE_STORED_VALUE = "FAKE-not-a-layout";

    private SystemPreferencesDao dao;
    private ChartSpaceLayoutService service;

    @BeforeEach
    void setUp() {
        dao = mock(SystemPreferencesDao.class);
        service = new ChartSpaceLayoutService(dao);
    }

    @Test
    @DisplayName("should load family practice when no preference is stored")
    void shouldLoadFamilyPractice_whenNoPreferenceStored() {
        when(dao.findPreferenceByName(KEY)).thenReturn(null);

        assertThat(service.loadClinicDefault()).isEqualTo(ChartSpaceLayout.FAMILY_PRACTICE);
    }

    @Test
    @DisplayName("should load eye care when it is stored")
    void shouldLoadEyeCare_whenStored() {
        when(dao.findPreferenceByName(KEY)).thenReturn(new SystemPreferences(KEY.name(), "eyeCare"));

        assertThat(service.loadClinicDefault()).isEqualTo(ChartSpaceLayout.EYE_CARE);
    }

    @Test
    @DisplayName("should load family practice and warn with the key only when the stored value is invalid")
    void shouldLoadFamilyPractice_whenStoredInvalid() {
        when(dao.findPreferenceByName(KEY)).thenReturn(new SystemPreferences(KEY.name(), FAKE_STORED_VALUE));

        try (LogCapture capture = LogCapture.forLogger(ChartSpaceLayoutService.class)) {
            ChartSpaceLayout loaded = service.loadClinicDefault();

            assertThat(loaded).isEqualTo(ChartSpaceLayout.FAMILY_PRACTICE);
            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(capture.messages().get(0)).contains(KEY.name()).doesNotContain(FAKE_STORED_VALUE);
        }
    }

    @Test
    @DisplayName("should propagate a persistence exception when loading")
    void shouldPropagatePersistenceException_whenLoading() {
        when(dao.findPreferenceByName(KEY)).thenThrow(new PersistenceException("FAKE-db-down"));

        assertThatThrownBy(() -> service.loadClinicDefault()).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("should upsert the layout id when saving")
    void shouldUpsertLayoutId_whenSaving() {
        service.saveClinicDefault(ChartSpaceLayout.EYE_CARE);

        verify(dao).upsertPreference(KEY, "eyeCare");
    }

    @Test
    @DisplayName("should run save at repeatable read as the upsert contract requires")
    void shouldRunSaveAtRepeatableRead_forUpsertContract() throws NoSuchMethodException {
        Method save = ChartSpaceLayoutService.class.getMethod("saveClinicDefault", ChartSpaceLayout.class);

        Transactional annotation = save.getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    @DisplayName("should load inside a read-only transaction")
    void shouldLoadAsReadOnlyTransaction_forLoadMethod() throws NoSuchMethodException {
        Method load = ChartSpaceLayoutService.class.getMethod("loadClinicDefault");

        Transactional annotation = load.getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();
    }
}
