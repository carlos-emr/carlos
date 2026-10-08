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
package io.github.carlos_emr.carlos.webserv.oauth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.CarlosProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the default of {@code oauth.scope.enforcement.enabled} (#4419): enforcement is on unless an
 * operator explicitly turns it off, so an install whose properties never mention the switch, which was
 * every packaged install when #4415 published {@code /ws/services}, enforces scopes.
 */
@DisplayName("OAuthScopeEnforcement default-on switch")
@Tag("unit")
@Tag("security")
class OAuthScopeEnforcementUnitTest {

    private String previousValue;
    private String previousLegacyAccess;

    @BeforeEach
    void captureFlag() {
        previousValue = CarlosProperties.getInstance().getProperty(OAuthScopeEnforcement.PROPERTY, null);
        previousLegacyAccess = CarlosProperties.getInstance().getProperty(OAuthScopeEnforcement.LEGACY_ACCESS_PROPERTY, null);
    }

    @AfterEach
    void restoreFlag() {
        restore(OAuthScopeEnforcement.PROPERTY, previousValue);
        restore(OAuthScopeEnforcement.LEGACY_ACCESS_PROPERTY, previousLegacyAccess);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, value);
        }
    }

    @Test
    @DisplayName("should enforce when the property is absent")
    void shouldEnforce_whenPropertyAbsent() {
        CarlosProperties.getInstance().remove(OAuthScopeEnforcement.PROPERTY);

        assertThat(OAuthScopeEnforcement.isEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "true", "TRUE", "yes", "on", "1", "flase", "disabled"})
    @DisplayName("should enforce for any value that is not an explicit off value")
    void shouldEnforce_forValueThatIsNotExplicitlyOff(String value) {
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.PROPERTY, value);

        assertThat(OAuthScopeEnforcement.isEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE", " False ", "no", "NO", "off", "Off"})
    @DisplayName("should not enforce for an explicit off value")
    void shouldNotEnforce_forExplicitOffValue(String value) {
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.PROPERTY, value);

        assertThat(OAuthScopeEnforcement.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should ship enforcement enabled in the bundled carlos.properties")
    void shouldShipEnabled_inBundledProperties() throws Exception {
        java.util.Properties bundled = new java.util.Properties();
        try (java.io.InputStream in = getClass().getResourceAsStream("/carlos.properties")) {
            assertThat(in).isNotNull();
            bundled.load(in);
        }

        assertThat(OAuthScopeEnforcement.isExplicitlyDisabled(bundled.getProperty(OAuthScopeEnforcement.PROPERTY)))
                .isFalse();
        assertThat(bundled.getProperty(OAuthScopeEnforcement.PROPERTY)).isEqualTo("true");
    }

    @Test
    @DisplayName("should run scoped whatever the legacy access value while enforcement is on")
    void shouldBeScoped_whenEnforcementOnRegardlessOfLegacyAccess() {
        CarlosProperties.getInstance().remove(OAuthScopeEnforcement.PROPERTY);
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.LEGACY_ACCESS_PROPERTY, "full");

        assertThat(OAuthScopeEnforcement.mode()).isEqualTo(OAuthScopeEnforcement.Mode.SCOPED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"restricted", "", "  ", "ful", "everything", "FULL ACCESS"})
    @DisplayName("should restrict legacy access unless the value is exactly full")
    void shouldRestrictLegacyAccess_forAnyValueButFull(String value) {
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.PROPERTY, "false");
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.LEGACY_ACCESS_PROPERTY, value);

        assertThat(OAuthScopeEnforcement.mode()).isEqualTo(OAuthScopeEnforcement.Mode.LEGACY_RESTRICTED);
        assertThat(OAuthScopeEnforcement.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should restrict legacy access when the legacy property is absent")
    void shouldRestrictLegacyAccess_whenLegacyPropertyAbsent() {
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.PROPERTY, "off");
        CarlosProperties.getInstance().remove(OAuthScopeEnforcement.LEGACY_ACCESS_PROPERTY);

        assertThat(OAuthScopeEnforcement.mode()).isEqualTo(OAuthScopeEnforcement.Mode.LEGACY_RESTRICTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"full", "FULL", " Full "})
    @DisplayName("should open full legacy access only for an explicit full value")
    void shouldOpenFullLegacyAccess_forExplicitFull(String value) {
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.PROPERTY, "false");
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.LEGACY_ACCESS_PROPERTY, value);

        assertThat(OAuthScopeEnforcement.mode()).isEqualTo(OAuthScopeEnforcement.Mode.LEGACY_FULL);
    }
}
