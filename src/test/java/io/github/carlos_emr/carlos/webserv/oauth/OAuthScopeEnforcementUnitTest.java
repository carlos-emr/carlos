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

    @BeforeEach
    void captureFlag() {
        previousValue = CarlosProperties.getInstance().getProperty(OAuthScopeEnforcement.PROPERTY, null);
    }

    @AfterEach
    void restoreFlag() {
        if (previousValue == null) {
            CarlosProperties.getInstance().remove(OAuthScopeEnforcement.PROPERTY);
        } else {
            CarlosProperties.getInstance().setProperty(OAuthScopeEnforcement.PROPERTY, previousValue);
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
}
