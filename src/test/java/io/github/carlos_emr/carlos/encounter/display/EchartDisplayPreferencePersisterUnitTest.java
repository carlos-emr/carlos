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
package io.github.carlos_emr.carlos.encounter.display;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Covers what the Preferences page's eChart section writes: posted Yes/No values, and nothing for a
 * setting that is absent or malformed.
 */
@Tag("unit")
@Tag("fast")
@Tag("encounter")
@DisplayName("EchartDisplayPreferencePersister")
class EchartDisplayPreferencePersisterUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    private UserPropertyDAO userPropertyDao;
    private EchartDisplayPreferencePersister persister;

    @BeforeEach
    void setUp() {
        userPropertyDao = mock(UserPropertyDAO.class);
        persister = new EchartDisplayPreferencePersister(userPropertyDao);
    }

    @Test
    @DisplayName("should write every posted setting as true or false")
    void shouldWriteEveryPostedSetting_asTrueOrFalse() {
        Map<String, String> form = new HashMap<>();
        for (EchartDisplayPreference preference : EchartDisplayPreference.values()) {
            form.put(preference.propertyName(), preference.defaultValue() ? "false" : "true");
        }

        int saved = persister.persist(PROVIDER_NO, form::get);

        assertThat(saved).isEqualTo(EchartDisplayPreference.values().length);
        verify(userPropertyDao).saveProp(PROVIDER_NO, "echart_show_timer", "false");
        verify(userPropertyDao).saveProp(PROVIDER_NO, "echart_show_ref_doc_widget", "true");
        verify(userPropertyDao).saveProp(PROVIDER_NO, "echart_show_ocean", "true");
    }

    @Test
    @DisplayName("should leave settings that were not posted untouched")
    void shouldLeaveSettingsUntouched_whenNotPosted() {
        int saved = persister.persist(PROVIDER_NO, Map.of("echart_show_HIN", "false")::get);

        assertThat(saved).isEqualTo(1);
        verify(userPropertyDao).saveProp(PROVIDER_NO, "echart_show_HIN", "false");
        verifyNoMoreInteractions(userPropertyDao);
    }

    @Test
    @DisplayName("should ignore a malformed value rather than switch the setting")
    void shouldIgnoreMalformedValue_ratherThanSwitchSetting() {
        int saved = persister.persist(PROVIDER_NO, Map.of("echart_show_ocean", "yes", "echart_show_timer", "")::get);

        assertThat(saved).isZero();
        verify(userPropertyDao, never()).saveProp(anyString(), anyString(), anyString());
    }
}
