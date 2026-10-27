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

import java.io.IOException;
import java.io.InputStream;
import java.util.OptionalLong;
import java.util.Properties;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.ServiceClient;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the access-token lifetime policy of issue #5288: the client's value, 3600 for none, a 60 s floor that
 * wins over the cap, the {@code oauth.access.token.max.lifetime} cap, and one warning per JVM for a bad cap.
 * Every {@code forClient} test sets the property itself; none depends on the environment.
 */
@DisplayName("OAuthAccessTokenLifetime policy")
@Tag("unit")
@Tag("security")
class OAuthAccessTokenLifetimeUnitTest extends CarlosUnitTestBase {

    private String previousMax;

    @BeforeEach
    void captureProperty() {
        previousMax = CarlosProperties.getInstance().getProperty(OAuthAccessTokenLifetime.MAX_LIFETIME_PROPERTY, null);
        OAuthAccessTokenLifetime.resetWarningForTest();
    }

    @AfterEach
    void restoreProperty() {
        setMax(previousMax);
    }

    private static void setMax(String value) {
        if (value == null) {
            CarlosProperties.getInstance().remove(OAuthAccessTokenLifetime.MAX_LIFETIME_PROPERTY);
        } else {
            CarlosProperties.getInstance().setProperty(OAuthAccessTokenLifetime.MAX_LIFETIME_PROPERTY, value);
        }
    }

    private static ServiceClient clientWith(Integer lifetime) {
        ServiceClient client = new ServiceClient();
        client.setLifetime(lifetime);
        return client;
    }

    @Test
    @DisplayName("should keep the client value when it is below the cap")
    void shouldKeepClientValue_whenBelowCap() {
        assertThat(OAuthAccessTokenLifetime.resolve(28800, 43200L)).isEqualTo(28800L);
    }

    @Test
    @DisplayName("should cap the client value when it is above the cap")
    void shouldCapClientValue_whenAboveCap() {
        assertThat(OAuthAccessTokenLifetime.resolve(100000, 43200L)).isEqualTo(43200L);
    }

    @Test
    @DisplayName("should cap the default when the cap is below 3600")
    void shouldCapDefault_whenCapBelowDefault() {
        assertThat(OAuthAccessTokenLifetime.resolve(null, 900L)).isEqualTo(900L);
    }

    @Test
    @DisplayName("should cap a client value that exceeds a small cap")
    void shouldCapClientValue_whenCapIsSmall() {
        assertThat(OAuthAccessTokenLifetime.resolve(1800, 900L)).isEqualTo(900L);
    }

    @Test
    @DisplayName("should parse a positive integer, trimmed or not")
    void shouldParseMax_whenPositiveInteger() {
        assertThat(OAuthAccessTokenLifetime.parseMax("7200")).isEqualTo(OptionalLong.of(7200L));
        assertThat(OAuthAccessTokenLifetime.parseMax(" 7200 ")).isEqualTo(OptionalLong.of(7200L));
    }

    @ParameterizedTest(name = "stored {0} -> 60")
    @ValueSource(ints = {1, 59, 60})
    @DisplayName("should raise a stored value below the floor to 60 seconds")
    void shouldApplyFloor_whenStoredValueBelowFloor(int stored) {
        assertThat(OAuthAccessTokenLifetime.resolve(stored, 43200L)).isEqualTo(60L);
    }

    @Test
    @DisplayName("should let the floor win over a cap below 60")
    void shouldLetFloorWin_whenCapBelowFloor() {
        assertThat(OAuthAccessTokenLifetime.resolve(null, 30L)).isEqualTo(60L);
    }

    @Test
    @DisplayName("should honour the client value through forClient when the cap is 43200")
    void shouldHonourClientValue_whenForClientAndCapDefault() {
        setMax("43200");
        assertThat(OAuthAccessTokenLifetime.forClient(clientWith(28800))).isEqualTo(28800L);
    }

    @Test
    @DisplayName("should read the cap from the property in forClient")
    void shouldReadCapProperty_whenForClient() {
        setMax("900");
        assertThat(OAuthAccessTokenLifetime.forClient(clientWith(28800))).isEqualTo(900L);
    }

    @Test
    @DisplayName("should use the default cap and warn once without the raw value when the property is invalid")
    void shouldWarnOnce_whenPropertyInvalid() {
        setMax("abc");
        try (LogCapture capture = LogCapture.forLogger(OAuthAccessTokenLifetime.class)) {
            assertThat(OAuthAccessTokenLifetime.forClient(clientWith(28800))).isEqualTo(28800L);
            assertThat(OAuthAccessTokenLifetime.forClient(clientWith(28800))).isEqualTo(28800L);

            assertThat(capture.events()).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(1);
            assertThat(capture.messages()).singleElement()
                    .asString()
                    .contains(OAuthAccessTokenLifetime.MAX_LIFETIME_PROPERTY)
                    .doesNotContain("abc");
        }
    }

    @Test
    @DisplayName("should ship the property in carlos.properties with the default cap")
    void shouldShipProperty_whenCarlosPropertiesLoaded() throws IOException {
        Properties shipped = new Properties();
        try (InputStream in = OAuthAccessTokenLifetimeUnitTest.class.getResourceAsStream("/carlos.properties")) {
            assertThat(in).as("carlos.properties on the classpath").isNotNull();
            shipped.load(in);
        }
        assertThat(shipped.containsKey(OAuthAccessTokenLifetime.MAX_LIFETIME_PROPERTY)).isTrue();
        assertThat(OAuthAccessTokenLifetime.parseMax(shipped.getProperty(OAuthAccessTokenLifetime.MAX_LIFETIME_PROPERTY)))
                .isEqualTo(OptionalLong.of(OAuthAccessTokenLifetime.DEFAULT_MAX_LIFETIME_SECONDS));
    }

    @ParameterizedTest(name = "property {0}")
    @ValueSource(strings = {"", "<removed>"})
    @DisplayName("should use the default cap without a warning when the property is blank or absent")
    void shouldNotWarn_whenPropertyBlankOrAbsent(String state) {
        setMax("<removed>".equals(state) ? null : state);
        // CarlosProperties logs its own WARN for a missing key on ITS logger; the capture is scoped to
        // OAuthAccessTokenLifetime only, so that expected line is not seen here.
        try (LogCapture capture = LogCapture.forLogger(OAuthAccessTokenLifetime.class)) {
            assertThat(OAuthAccessTokenLifetime.forClient(clientWith(28800))).isEqualTo(28800L);
            assertThat(capture.events()).filteredOn(e -> e.getLevel() == Level.WARN).isEmpty();
        }
    }

    @ParameterizedTest(name = "stored {0} -> 3600")
    @NullSource
    @ValueSource(ints = {0, -5})
    @DisplayName("should use 3600 when the stored value is null, zero or negative")
    void shouldUseDefault_whenStoredValueNotPositive(Integer stored) {
        assertThat(OAuthAccessTokenLifetime.resolve(stored, 43200L)).isEqualTo(3600L);
    }

    @ParameterizedTest(name = "parseMax({0}) is empty")
    @NullSource
    @ValueSource(strings = {"", "abc", "0", "-1", "99999999999999999999", "1.5"})
    @DisplayName("should reject a cap that is blank, not an integer, or not positive")
    void shouldRejectMax_whenInvalid(String value) {
        assertThat(OAuthAccessTokenLifetime.parseMax(value)).isEmpty();
    }

    @Test
    @DisplayName("should use 3600 when the client is null")
    void shouldUseDefault_whenClientNull() {
        setMax("43200");
        assertThat(OAuthAccessTokenLifetime.forClient(null)).isEqualTo(3600L);
    }
}
