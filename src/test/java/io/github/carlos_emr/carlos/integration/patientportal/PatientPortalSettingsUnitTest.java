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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

/**
 * Configuration contract for the CARLOS to patient-portal channel.
 *
 * <p>The portal grants clinic-wide staff powers to anything holding its service token, so these
 * tests pin the properties that keep that token usable only over TLS, to one configured host, and
 * out of logs.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PatientPortalSettings")
class PatientPortalSettingsUnitTest {

    private static final String BASE_URL_KEY = "patient_portal.base_url";
    private static final String CLINIC_ID_KEY = "patient_portal.clinic_id";
    private static final String SERVICE_TOKEN_KEY = "patient_portal.service_token";
    private static final String STAFF_ASSERTION_KEY =
            "patient_portal.staff_assertion.private_key";
    private static final String CONNECT_TIMEOUT_KEY = "patient_portal.timeout.connect.ms";
    private static final String READ_TIMEOUT_KEY = "patient_portal.timeout.read.ms";

    private static final String TOKEN = "portal-service-token-value-000001";
    private static final String ASSERTION_PRIVATE_KEY = PortalTestKeys.PRIVATE_KEY;

    private Map<String, String> validProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(BASE_URL_KEY, "https://portal.clinic.example");
        properties.put(CLINIC_ID_KEY, "maplecreek");
        properties.put(SERVICE_TOKEN_KEY, TOKEN);
        properties.put(STAFF_ASSERTION_KEY, ASSERTION_PRIVATE_KEY);
        return properties;
    }

    @Nested
    @DisplayName("valid configuration")
    class ValidConfiguration {

        @Test
        @DisplayName("should load every configured value")
        void shouldLoadSettings_whenAllRequiredPropertiesPresent() {
            PatientPortalSettings settings = PatientPortalSettings.fromProperties(validProperties());

            assertThat(settings.baseUrl()).isEqualTo("https://portal.clinic.example");
            assertThat(settings.clinicId()).isEqualTo("maplecreek");
            assertThat(settings.serviceToken().expose()).isEqualTo(TOKEN);
            assertThat(settings.staffAssertionPrivateKey().expose())
                    .isEqualTo(ASSERTION_PRIVATE_KEY);
        }

        @Test
        @DisplayName("should apply default timeouts when none are configured")
        void shouldApplyDefaultTimeouts_whenNotConfigured() {
            PatientPortalSettings settings = PatientPortalSettings.fromProperties(validProperties());

            assertThat(settings.connectTimeout()).isEqualTo(Duration.ofMillis(5000));
            assertThat(settings.readTimeout()).isEqualTo(Duration.ofMillis(15000));
        }

        @Test
        @DisplayName("should honour configured timeouts")
        void shouldUseConfiguredTimeouts_whenPresent() {
            Map<String, String> properties = validProperties();
            properties.put(CONNECT_TIMEOUT_KEY, "2500");
            properties.put(READ_TIMEOUT_KEY, "30000");

            PatientPortalSettings settings = PatientPortalSettings.fromProperties(properties);

            assertThat(settings.connectTimeout()).isEqualTo(Duration.ofMillis(2500));
            assertThat(settings.readTimeout()).isEqualTo(Duration.ofMillis(30000));
        }

        @Test
        @DisplayName("should strip a trailing slash so request paths join cleanly")
        void shouldStripTrailingSlash_fromBaseUrl() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "https://portal.clinic.example/");

            PatientPortalSettings settings = PatientPortalSettings.fromProperties(properties);

            assertThat(settings.baseUrl()).isEqualTo("https://portal.clinic.example");
        }

        @Test
        @DisplayName("should retain a path prefix for a portal mounted below its origin")
        void shouldRetainPathPrefix_whenPortalUsesOne() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "https://portal.clinic.example/patient-portal/");

            PatientPortalSettings settings = PatientPortalSettings.fromProperties(properties);

            assertThat(settings.baseUrl())
                    .isEqualTo("https://portal.clinic.example/patient-portal");
        }

        @Test
        @DisplayName("should trim surrounding whitespace from configured values")
        void shouldTrimValues_whenPropertiesCarryWhitespace() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "  https://portal.clinic.example  ");
            properties.put(CLINIC_ID_KEY, "  maplecreek  ");
            properties.put(SERVICE_TOKEN_KEY, "  " + TOKEN + "  ");

            PatientPortalSettings settings = PatientPortalSettings.fromProperties(properties);

            assertThat(settings.baseUrl()).isEqualTo("https://portal.clinic.example");
            assertThat(settings.clinicId()).isEqualTo("maplecreek");
            assertThat(settings.serviceToken().expose()).isEqualTo(TOKEN);
        }

        @Test
        @DisplayName("should accept the portal's full clinic id length")
        void shouldAccept_whenClinicIdUsesPortalMaximumLength() {
            Map<String, String> properties = validProperties();
            properties.put(CLINIC_ID_KEY, "c".repeat(64));

            assertThat(PatientPortalSettings.fromProperties(properties).clinicId()).hasSize(64);
        }

        @Test
        @DisplayName("should normalize the service token through direct construction too")
        void shouldTrimServiceToken_whenCanonicalConstructorReceivesPadding() {
            PatientPortalSettings settings =
                    new PatientPortalSettings(
                            "https://portal.clinic.example",
                            "maplecreek",
                            PortalSecret.of("  " + TOKEN + "  "),
                            PortalSecret.of(ASSERTION_PRIVATE_KEY),
                            Duration.ofSeconds(5),
                            Duration.ofSeconds(15),
                            java.util.Set.of());

            assertThat(settings.serviceToken().expose()).isEqualTo(TOKEN);
        }
    }

    @Nested
    @DisplayName("transport security")
    class TransportSecurity {

        @Test
        @DisplayName("should reject a plaintext base URL")
        void shouldReject_whenBaseUrlIsNotHttps() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "http://portal.clinic.example");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining("https");
        }

        @Test
        @DisplayName("should reject a plaintext loopback base URL")
        void shouldReject_whenBaseUrlIsPlaintextLoopback() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "http://127.0.0.1:8090");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }

        @Test
        @DisplayName("should reject credentials embedded in the base URL")
        void shouldReject_whenBaseUrlCarriesUserInfo() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "https://someone:secret@portal.clinic.example");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }

        @Test
        @DisplayName("should reject a base URL carrying a query or fragment")
        void shouldReject_whenBaseUrlCarriesQueryOrFragment() {
            Map<String, String> withQuery = validProperties();
            withQuery.put(BASE_URL_KEY, "https://portal.clinic.example?a=1");
            Map<String, String> withFragment = validProperties();
            withFragment.put(BASE_URL_KEY, "https://portal.clinic.example#x");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(withQuery))
                    .isInstanceOf(PatientPortalConfigurationException.class);
            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(withFragment))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }


        /**
         * Validation used to live only in the factory, leaving the record's canonical constructor
         * public and unchecked — a plaintext destination for the service token compiled cleanly.
         */
        @Test
        @DisplayName("should refuse a plaintext base URL through the constructor too")
        void shouldReject_whenPlaintextIsPassedToTheCanonicalConstructor() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalSettings(
                                            "http://evil.example",
                                            "maplecreek",
                                            PortalSecret.of(TOKEN),
                                            PortalSecret.of(ASSERTION_PRIVATE_KEY),
                                            Duration.ofSeconds(5),
                                            Duration.ofSeconds(15),
                                            java.util.Set.of()))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }

        @Test
        @DisplayName("should refuse a non-positive timeout through the constructor too")
        void shouldReject_whenTimeoutIsNonPositiveInTheConstructor() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalSettings(
                                            "https://portal.clinic.example",
                                            "maplecreek",
                                            PortalSecret.of(TOKEN),
                                            PortalSecret.of(ASSERTION_PRIVATE_KEY),
                                            Duration.ZERO,
                                            Duration.ofSeconds(15),
                                            java.util.Set.of()))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }

        @Test
        @DisplayName("should refuse a timeout that becomes zero milliseconds")
        void shouldReject_whenTimeoutIsBelowTransportPrecision() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalSettings(
                                            "https://portal.clinic.example",
                                            "maplecreek",
                                            PortalSecret.of(TOKEN),
                                            PortalSecret.of(ASSERTION_PRIVATE_KEY),
                                            Duration.ofNanos(1),
                                            Duration.ofSeconds(15),
                                            Set.of()))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(CONNECT_TIMEOUT_KEY);
        }

        @Test
        @DisplayName("should refuse a timeout that cannot be represented in milliseconds")
        void shouldReject_whenTimeoutOverflowsTransportPrecision() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalSettings(
                                            "https://portal.clinic.example",
                                            "maplecreek",
                                            PortalSecret.of(TOKEN),
                                            PortalSecret.of(ASSERTION_PRIVATE_KEY),
                                            Duration.ofSeconds(Long.MAX_VALUE),
                                            Duration.ofSeconds(15),
                                            Set.of()))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(CONNECT_TIMEOUT_KEY);
        }

        @Test
        @DisplayName("should refuse a missing service token through the constructor too")
        void shouldReject_whenServiceTokenIsNullInTheConstructor() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalSettings(
                                            "https://portal.clinic.example",
                                            "maplecreek",
                                            null,
                                            PortalSecret.of(ASSERTION_PRIVATE_KEY),
                                            Duration.ofSeconds(5),
                                            Duration.ofSeconds(15),
                                            java.util.Set.of()))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }

        @Test
        @DisplayName("should reject a base URL that is not a valid absolute URI")
        void shouldReject_whenBaseUrlIsMalformed() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "https://not a host");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }

        @Test
        @DisplayName("should not retain credentials from a malformed base URL")
        void shouldOmitEmbeddedCredential_whenBaseUrlIsMalformed() {
            String syntheticCredential = "synthetic-url-password";
            Map<String, String> properties = validProperties();
            properties.put(
                    BASE_URL_KEY,
                    "https://user:" + syntheticCredential + "@portal.clinic.example/bad path");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageNotContaining(syntheticCredential)
                    .hasNoCause();
        }
    }

    @Nested
    @DisplayName("fail closed")
    class FailClosed {

        @Test
        @DisplayName("should recognize optional-only values as an attempted configuration")
        void shouldTreatOptionalSettingAsConfigured_whenRequiredValuesAreMissing() {
            for (String key : java.util.List.of(
                    CONNECT_TIMEOUT_KEY,
                    READ_TIMEOUT_KEY,
                    PatientPortalSettings.CERTIFICATE_PINS_KEY)) {
                assertThat(PatientPortalSettings.isConfigured(candidate ->
                                candidate.equals(key) ? "configured" : null))
                        .as(key)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("should fail when nothing is configured at all")
        void shouldThrow_whenNoPropertiesConfigured() {
            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(Map.of()))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }

        @Test
        @DisplayName("should fail when the base URL is absent")
        void shouldThrow_whenBaseUrlIsMissing() {
            Map<String, String> properties = validProperties();
            properties.remove(BASE_URL_KEY);

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(BASE_URL_KEY);
        }

        @Test
        @DisplayName("should fail when the service token is absent")
        void shouldThrow_whenServiceTokenIsMissing() {
            Map<String, String> properties = validProperties();
            properties.remove(SERVICE_TOKEN_KEY);

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(SERVICE_TOKEN_KEY);
        }

        @Test
        @DisplayName("should fail when the service token is blank")
        void shouldThrow_whenServiceTokenIsBlank() {
            Map<String, String> properties = validProperties();
            properties.put(SERVICE_TOKEN_KEY, "   ");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }

        @Test
        @DisplayName("should reject service tokens that cannot be used in an HTTP header")
        void shouldThrow_whenServiceTokenIsShortOrNotVisibleAscii() {
            Map<String, String> shortToken = validProperties();
            shortToken.put(SERVICE_TOKEN_KEY, "x".repeat(31));
            Map<String, String> control = validProperties();
            control.put(SERVICE_TOKEN_KEY, "x".repeat(16) + "\n" + "x".repeat(16));
            Map<String, String> whitespace = validProperties();
            whitespace.put(SERVICE_TOKEN_KEY, "x".repeat(16) + " " + "x".repeat(16));
            Map<String, String> unicode = validProperties();
            unicode.put(SERVICE_TOKEN_KEY, "x".repeat(31) + "é");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(shortToken))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(SERVICE_TOKEN_KEY);
            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(control))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(SERVICE_TOKEN_KEY);
            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(whitespace))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(SERVICE_TOKEN_KEY);
            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(unicode))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(SERVICE_TOKEN_KEY);
        }

        @Test
        @DisplayName("should fail when the staff assertion private key is absent")
        void shouldThrow_whenStaffAssertionPrivateKeyIsMissing() {
            Map<String, String> properties = validProperties();
            properties.remove(STAFF_ASSERTION_KEY);

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(STAFF_ASSERTION_KEY);
        }

        @Test
        @DisplayName("should reject a private key that is not canonical Ed25519 PKCS8")
        void shouldThrow_whenStaffAssertionPrivateKeyIsInvalid() {
            Map<String, String> properties = validProperties();
            properties.put(STAFF_ASSERTION_KEY, "not-a-private-key");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(STAFF_ASSERTION_KEY)
                    .hasMessageNotContaining("not-a-private-key");
        }

        @Test
        @DisplayName("should reject padded base64url private key configuration")
        void shouldThrow_whenStaffAssertionPrivateKeyIsNotCanonical() {
            Map<String, String> properties = validProperties();
            properties.put(STAFF_ASSERTION_KEY, ASSERTION_PRIVATE_KEY + "=");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining("unpadded base64url");
        }

        @Test
        @DisplayName("should fail when the clinic id is absent")
        void shouldThrow_whenClinicIdIsMissing() {
            Map<String, String> properties = validProperties();
            properties.remove(CLINIC_ID_KEY);

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(CLINIC_ID_KEY);
        }

        @Test
        @DisplayName("should reject a clinic id outside the portal contract")
        void shouldThrow_whenClinicIdExceedsPortalLimitOrContainsUnsupportedCharacters() {
            Map<String, String> tooLong = validProperties();
            tooLong.put(CLINIC_ID_KEY, "c".repeat(65));
            Map<String, String> unsupported = validProperties();
            unsupported.put(CLINIC_ID_KEY, "clinic/other");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(tooLong))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(CLINIC_ID_KEY);
            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(unsupported))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(CLINIC_ID_KEY);
        }

        @Test
        @DisplayName("should fail when a timeout is not a positive number")
        void shouldThrow_whenTimeoutIsNotPositive() {
            Map<String, String> zero = validProperties();
            zero.put(CONNECT_TIMEOUT_KEY, "0");
            Map<String, String> negative = validProperties();
            negative.put(READ_TIMEOUT_KEY, "-1");
            Map<String, String> notANumber = validProperties();
            notANumber.put(CONNECT_TIMEOUT_KEY, "soon");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(zero))
                    .isInstanceOf(PatientPortalConfigurationException.class);
            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(negative))
                    .isInstanceOf(PatientPortalConfigurationException.class);
            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(notANumber))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }
    }

    @Nested
    @DisplayName("secret handling")
    class SecretHandling {

        /**
         * A record's generated {@code toString} prints every component, so the default would put the
         * service token into any log line, stack trace, or debugger view that renders the settings.
         * The token grants clinic-wide staff powers on the portal, so it must never render.
         */
        @Test
        @DisplayName("should never render the service token")
        void shouldRedactServiceToken_inToStringOutput() {
            PatientPortalSettings settings = PatientPortalSettings.fromProperties(validProperties());

            assertThat(settings.toString()).doesNotContain(TOKEN);
            assertThat(settings.toString()).doesNotContain(ASSERTION_PRIVATE_KEY);
            assertThat(settings.toString()).contains("REDACTED");
        }

        @Test
        @DisplayName("should still describe the endpoint it points at")
        void shouldDescribeEndpoint_inToStringOutput() {
            PatientPortalSettings settings = PatientPortalSettings.fromProperties(validProperties());

            assertThat(settings.toString()).contains("portal.clinic.example");
            assertThat(settings.toString()).contains("maplecreek");
        }

        @Test
        @DisplayName("should keep the configuration failure message free of the token")
        void shouldOmitServiceToken_fromConfigurationFailureMessage() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "http://portal.clinic.example");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageNotContaining(TOKEN);
        }
    }

    /**
     * Pins are the one optional value that is a security control, so a mistake in them must not be
     * discovered by a failed handshake.
     */
    @Nested
    @DisplayName("certificate pins")
    class CertificatePins {

        @Test
        @DisplayName("should report a null pin as a configuration failure")
        void shouldReject_whenDirectPinSetContainsNull() {
            Set<String> pins = new HashSet<>();
            pins.add(null);

            assertThatThrownBy(
                            () ->
                                    new PatientPortalSettings(
                                            "https://portal.clinic.example",
                                            "maplecreek",
                                            PortalSecret.of(TOKEN),
                                            PortalSecret.of(ASSERTION_PRIVATE_KEY),
                                            Duration.ofSeconds(5),
                                            Duration.ofSeconds(15),
                                            pins))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(PatientPortalSettings.CERTIFICATE_PINS_KEY);
        }

        @Test
        @DisplayName("should accept a comma separated list, for a planned key rotation")
        void shouldParseEveryPin_whenSeveralAreConfigured() {
            Map<String, String> properties = validProperties();
            properties.put(
                    PatientPortalSettings.CERTIFICATE_PINS_KEY,
                    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=, sha256/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");

            PatientPortalSettings settings = PatientPortalSettings.fromProperties(properties);

            assertThat(settings.certificatePins())
                    .containsExactlyInAnyOrder("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "sha256/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        }

        /**
         * The failure this catches is mundane: an operator follows the openssl recipe and pastes
         * the base64 without the sha256/ prefix. Rejected at construction, it appears alongside the
         * deployment's other configuration errors. Rejected only at the socket factory, it survives
         * to the first handshake and reads as "did not match any configured pin" — which describes
         * a rotated key, and sends whoever is debugging it to the wrong place entirely.
         */
        @Test
        @DisplayName("should reject a pin without the sha256/ prefix, at configuration time")
        void shouldThrow_whenAPinIsMissingItsPrefix() {
            Map<String, String> properties = validProperties();
            properties.put(
                    PatientPortalSettings.CERTIFICATE_PINS_KEY,
                    "K2v8VhV0mS1QcM0kQ9x1kZ0m0Q9x1kZ0m0Q9x1kZ0m0=");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining(PatientPortalSettings.CERTIFICATE_PINS_KEY);
        }

        /**
         * Absent means unpinned, which is a supported deployment — but it is also what a misspelled
         * property key produces, so the transport says which mode it is in when it is built.
         */
        @Test
        @DisplayName("should treat an absent or blank value as no pinning")
        void shouldReturnNoPins_whenTheValueIsAbsentOrBlank() {
            assertThat(PatientPortalSettings.fromProperties(validProperties()).certificatePins())
                    .isEmpty();

            Map<String, String> blank = validProperties();
            blank.put(PatientPortalSettings.CERTIFICATE_PINS_KEY, "   ");
            assertThat(PatientPortalSettings.fromProperties(blank).certificatePins()).isEmpty();
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 65536, 2147483647})
    void rejectsInvalidEndpointPortAsConfigurationFailure(int port) {
        Map<String, String> properties = validProperties();
        properties.put(BASE_URL_KEY, "https://portal.example:" + port);
        assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                .isInstanceOf(PatientPortalConfigurationException.class)
                .hasMessageContaining(BASE_URL_KEY);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 443, 65535})
    void acceptsValidExplicitEndpointPorts(int port) {
        Map<String, String> properties = validProperties();
        String url = "https://portal.example:" + port;
        properties.put(BASE_URL_KEY, url);
        assertThat(PatientPortalSettings.fromProperties(properties).baseUrl()).isEqualTo(url);
    }
}
