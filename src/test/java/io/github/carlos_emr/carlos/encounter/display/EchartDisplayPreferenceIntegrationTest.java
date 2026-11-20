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
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round trip of the per-provider eChart display settings through the real {@code property} table:
 * what the Preferences page's eChart section saves is what the chart then reads, settings are kept
 * per provider, and the clinic-wide Ocean switch still overrides a provider's opt-in.
 */
@Tag("integration")
@Tag("dao")
@Tag("encounter")
@DisplayName("eChart display preferences round trip")
class EchartDisplayPreferenceIntegrationTest extends CarlosTestBase {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private UserPropertyDAO userPropertyDao;

    @Autowired
    private SystemPreferencesDao systemPreferencesDao;

    private EchartDisplayPreferencePersister persister;
    private EchartDisplayPreferenceResolver resolver;

    @BeforeEach
    void setUp() {
        persister = new EchartDisplayPreferencePersister(userPropertyDao);
        resolver = new EchartDisplayPreferenceResolver(userPropertyDao, systemPreferencesDao);
    }

    @Test
    @Tag("read")
    @DisplayName("should give a provider who never saved the section the requested defaults")
    void shouldGiveDefaults_toProviderWhoNeverSaved() {
        EchartDisplayPreferences preferences = resolver.resolve(uniqueProviderNo());

        assertThat(preferences.showTimer()).isTrue();
        assertThat(preferences.groupDocumentsByType()).isTrue();
        assertThat(preferences.showDob()).isTrue();
        assertThat(preferences.showCell()).isTrue();
        assertThat(preferences.showHin()).isTrue();
        assertThat(preferences.showEmail()).isTrue();
        assertThat(preferences.showFamilyDoctor()).isTrue();
        assertThat(preferences.showReferringDoctor()).isFalse();
        assertThat(preferences.showOcean()).isFalse();
    }

    @Test
    @Tag("create")
    @Tag("update")
    @DisplayName("should read back every setting the section saved, and update in place on a second save")
    void shouldReadBackSavedSettings_andUpdateInPlace() {
        String providerNo = uniqueProviderNo();
        Map<String, String> form = new HashMap<>();
        for (EchartDisplayPreference preference : EchartDisplayPreference.values()) {
            form.put(preference.propertyName(), String.valueOf(!preference.defaultValue()));
        }

        persister.persist(providerNo, form::get);
        userPropertyDao.flush();
        EchartDisplayPreferences inverted = resolver.resolve(providerNo);
        for (EchartDisplayPreference preference : EchartDisplayPreference.values()) {
            assertThat(inverted.isEnabled(preference)).as(preference.name()).isEqualTo(!preference.defaultValue());
        }

        persister.persist(providerNo, Map.of("echart_show_timer", "true")::get);
        userPropertyDao.flush();

        assertThat(resolver.resolve(providerNo).showTimer()).isTrue();
        assertThat(resolver.resolve(providerNo).showHin()).isFalse();
        assertThat(userPropertyDao.getDemographicProperties(providerNo))
                .extracting(UserProperty::getName)
                .as("a second save updates the existing rows rather than adding duplicates")
                .hasSize(EchartDisplayPreference.values().length)
                .doesNotHaveDuplicates();
    }

    @Test
    @Tag("read")
    @DisplayName("should keep one provider's settings from affecting another's")
    void shouldKeepSettingsPerProvider() {
        String hidesHin = uniqueProviderNo();
        String other = uniqueProviderNo();

        persister.persist(hidesHin, Map.of("echart_show_HIN", "false")::get);
        userPropertyDao.flush();

        assertThat(resolver.resolve(hidesHin).showHin()).isFalse();
        assertThat(resolver.resolve(other).showHin()).isTrue();
    }

    @Test
    @Tag("read")
    @DisplayName("should show Ocean only when the provider opted in and the clinic switch allows it")
    void shouldShowOcean_onlyWhenProviderOptedInAndClinicAllows() {
        String providerNo = uniqueProviderNo();
        persister.persist(providerNo, Map.of("echart_show_ocean", "true")::get);
        userPropertyDao.flush();
        EchartDisplayPreferences optedIn = resolver.resolve(providerNo);

        SystemPreferences clinicSwitch = systemPreferencesDao.findPreferenceByName(
                SystemPreferences.ECHART_PREFERENCE_KEYS.echart_show_ocean);
        if (clinicSwitch == null) {
            assertThat(resolver.isOceanShown(optedIn)).as("an unset clinic switch allows Ocean").isTrue();
            clinicSwitch = new SystemPreferences("echart_show_ocean", "false");
            systemPreferencesDao.persist(clinicSwitch);
        } else {
            clinicSwitch.setValue("false");
            systemPreferencesDao.merge(clinicSwitch);
        }
        systemPreferencesDao.flush();

        assertThat(resolver.isOceanShown(optedIn)).as("the clinic switch turns Ocean off for everyone").isFalse();
        assertThat(resolver.isOceanShown(resolver.resolve(uniqueProviderNo()))).isFalse();
    }

    private static String uniqueProviderNo() {
        return String.format("E%05d", SEQUENCE.incrementAndGet());
    }
}
