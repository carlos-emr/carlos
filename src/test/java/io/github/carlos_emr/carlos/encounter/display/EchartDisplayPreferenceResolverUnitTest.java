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

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers how the resolver reads a provider's eChart display settings and how the clinic-wide Ocean
 * switch combines with the provider's own opt-in.
 */
@Tag("unit")
@Tag("fast")
@Tag("encounter")
@DisplayName("EchartDisplayPreferenceResolver")
class EchartDisplayPreferenceResolverUnitTest extends CarlosUnitTestBase {

    private UserPropertyDAO userPropertyDao;
    private SystemPreferencesDao systemPreferencesDao;
    private EchartDisplayPreferenceResolver resolver;

    @BeforeEach
    void setUp() {
        userPropertyDao = mock(UserPropertyDAO.class);
        systemPreferencesDao = mock(SystemPreferencesDao.class);
        resolver = new EchartDisplayPreferenceResolver(userPropertyDao, systemPreferencesDao);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("should read only the eChart settings, in one query, for the given provider")
    void shouldReadOnlyEchartSettings_inOneQuery() {
        when(userPropertyDao.getProviderPropertiesByNames(eq("999998"), any()))
                .thenReturn(Map.of("echart_show_HIN", "false"));

        EchartDisplayPreferences preferences = resolver.resolve("999998");

        ArgumentCaptor<Collection<String>> names = ArgumentCaptor.forClass(Collection.class);
        verify(userPropertyDao).getProviderPropertiesByNames(eq("999998"), names.capture());
        assertThat(names.getValue()).hasSize(EchartDisplayPreference.values().length)
                .contains("echart_show_timer", "echart_show_ocean");
        assertThat(preferences.showHin()).isFalse();
        assertThat(preferences.showTimer()).isTrue();
    }

    @Test
    @DisplayName("should return the defaults without querying when there is no provider")
    void shouldReturnDefaults_whenProviderMissing() {
        assertThat(resolver.resolve(null).showFamilyDoctor()).isTrue();
        assertThat(resolver.resolve(" ").showOcean()).isFalse();
        verify(userPropertyDao, never()).getProviderPropertiesByNames(anyString(), any());
    }

    @Test
    @DisplayName("should hide Ocean without consulting the clinic when the provider has not opted in")
    void shouldHideOcean_whenProviderHasNotOptedIn() {
        assertThat(resolver.isOceanShown(EchartDisplayPreferences.defaults())).isFalse();
        verifyNoInteractions(systemPreferencesDao);
    }

    @Test
    @DisplayName("should show Ocean for an opted-in provider when the clinic never saved its switch")
    void shouldShowOcean_whenOptedInAndClinicSwitchUnset() {
        assertThat(resolver.isOceanShown(optedIn())).isTrue();
    }

    @Test
    @DisplayName("should show Ocean for an opted-in provider when the clinic switch is on")
    void shouldShowOcean_whenOptedInAndClinicSwitchOn() {
        when(systemPreferencesDao.findPreferenceByName(SystemPreferences.ECHART_PREFERENCE_KEYS.echart_show_ocean))
                .thenReturn(new SystemPreferences("echart_show_ocean", "true"));

        assertThat(resolver.isOceanShown(optedIn())).isTrue();
    }

    @Test
    @DisplayName("should let the clinic switch turn Ocean off for an opted-in provider")
    void shouldHideOcean_whenClinicSwitchOff() {
        when(systemPreferencesDao.findPreferenceByName(SystemPreferences.ECHART_PREFERENCE_KEYS.echart_show_ocean))
                .thenReturn(new SystemPreferences("echart_show_ocean", "false"));

        assertThat(resolver.isOceanShown(optedIn())).isFalse();
    }

    private static EchartDisplayPreferences optedIn() {
        return EchartDisplayPreferences.fromStoredValues(Map.of("echart_show_ocean", "true"));
    }
}
