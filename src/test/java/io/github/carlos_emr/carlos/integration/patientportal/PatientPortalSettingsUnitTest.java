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
import java.util.Map;
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
    private static final String CONNECT_TIMEOUT_KEY = "patient_portal.timeout.connect.ms";
    private static final String READ_TIMEOUT_KEY = "patient_portal.timeout.read.ms";

    private static final String TOKEN = "portal-service-token-value-000001";

    private Map<String, String> validProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(BASE_URL_KEY, "https://portal.clinic.example");
        properties.put(CLINIC_ID_KEY, "maplecreek");
        properties.put(SERVICE_TOKEN_KEY, TOKEN);
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
            assertThat(settings.serviceToken()).isEqualTo(TOKEN);
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
        @DisplayName("should trim surrounding whitespace from configured values")
        void shouldTrimValues_whenPropertiesCarryWhitespace() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "  https://portal.clinic.example  ");
            properties.put(CLINIC_ID_KEY, "  maplecreek  ");
            properties.put(SERVICE_TOKEN_KEY, "  " + TOKEN + "  ");

            PatientPortalSettings settings = PatientPortalSettings.fromProperties(properties);

            assertThat(settings.baseUrl()).isEqualTo("https://portal.clinic.example");
            assertThat(settings.clinicId()).isEqualTo("maplecreek");
            assertThat(settings.serviceToken()).isEqualTo(TOKEN);
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

        @Test
        @DisplayName("should reject a base URL that is not a valid absolute URI")
        void shouldReject_whenBaseUrlIsMalformed() {
            Map<String, String> properties = validProperties();
            properties.put(BASE_URL_KEY, "https://not a host");

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }
    }

    @Nested
    @DisplayName("fail closed")
    class FailClosed {

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
        @DisplayName("should fail when the clinic id is absent")
        void shouldThrow_whenClinicIdIsMissing() {
            Map<String, String> properties = validProperties();
            properties.remove(CLINIC_ID_KEY);

            assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
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
}
