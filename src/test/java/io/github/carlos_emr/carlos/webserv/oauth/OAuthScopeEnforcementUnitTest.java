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
 * Pins the OAuth access-mode switches: with neither property set, which is every packaged install when
 * #4415 published {@code /ws/services}, a client gets the restricted legacy access; only an explicit on
 * value enforces scopes and only an explicit {@code full} widens legacy access.
 */
@DisplayName("OAuthScopeEnforcement mode switches")
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
    @DisplayName("should limit clients to the legacy integration endpoints when both properties are absent")
    void shouldRestrictLegacyAccess_whenBothPropertiesAbsent() {
        CarlosProperties.getInstance().remove(OAuthScopeEnforcement.PROPERTY);
        CarlosProperties.getInstance().remove(OAuthScopeEnforcement.LEGACY_ACCESS_PROPERTY);

        assertThat(OAuthScopeEnforcement.mode()).isEqualTo(OAuthScopeEnforcement.Mode.LEGACY_RESTRICTED);
        assertThat(OAuthScopeEnforcement.isEnabled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", " True ", "yes", "YES", "on", "On"})
    @DisplayName("should enforce scopes only for an explicit on value")
    void shouldEnforce_forExplicitOnValue(String value) {
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.PROPERTY, value);

        assertThat(OAuthScopeEnforcement.isEnabled()).isTrue();
        assertThat(OAuthScopeEnforcement.mode()).isEqualTo(OAuthScopeEnforcement.Mode.SCOPED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "false", "no", "off", "1", "ture", "enabled", "scoped"})
    @DisplayName("should not enforce for any value that is not an explicit on value")
    void shouldNotEnforce_forValueThatIsNotExplicitlyOn(String value) {
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.PROPERTY, value);
        CarlosProperties.getInstance().remove(OAuthScopeEnforcement.LEGACY_ACCESS_PROPERTY);

        assertThat(OAuthScopeEnforcement.isEnabled()).isFalse();
        assertThat(OAuthScopeEnforcement.mode()).isEqualTo(OAuthScopeEnforcement.Mode.LEGACY_RESTRICTED);
    }

    @Test
    @DisplayName("should ship the restricted legacy default in the bundled carlos.properties")
    void shouldShipRestrictedLegacyDefault_inBundledProperties() throws Exception {
        java.util.Properties bundled = new java.util.Properties();
        try (java.io.InputStream in = getClass().getResourceAsStream("/carlos.properties")) {
            assertThat(in).isNotNull();
            bundled.load(in);
        }

        assertThat(OAuthScopeEnforcement.isExplicitlyEnabled(bundled.getProperty(OAuthScopeEnforcement.PROPERTY)))
                .isFalse();
        assertThat(bundled.getProperty(OAuthScopeEnforcement.PROPERTY)).isEqualTo("false");
        assertThat(OAuthScopeEnforcement.isFullLegacyAccess(bundled.getProperty(OAuthScopeEnforcement.LEGACY_ACCESS_PROPERTY)))
                .isFalse();
    }

    @Test
    @DisplayName("should run scoped whatever the legacy access value while enforcement is on")
    void shouldBeScoped_whenEnforcementOnRegardlessOfLegacyAccess() {
        CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.PROPERTY, "true");
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
