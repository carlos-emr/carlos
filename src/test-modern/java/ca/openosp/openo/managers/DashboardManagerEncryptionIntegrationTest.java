/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.managers;

import ca.openosp.OscarProperties;
import ca.openosp.openo.commn.dao.ClinicDAO;
import ca.openosp.openo.commn.model.Clinic;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.test.base.OpenOTestBase;
import ca.openosp.openo.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Integration tests for DashboardManager encryption functionality.
 * Tests the migration from jasypt to Spring Security Crypto.
 *
 * @since 2026-01-28
 */
@Tag("integration")
@Tag("encryption")
@Tag("dashboard")
class DashboardManagerEncryptionIntegrationTest extends OpenOTestBase {

    @Autowired
    private DashboardManager dashboardManager;

    @Autowired
    private ClinicDAO clinicDAO;

    private LoggedInInfo loggedInInfo;
    private Provider testProvider;
    private Clinic testClinic;

    @BeforeEach
    void setUp() {
        // Set up test clinic
        testClinic = new Clinic();
        testClinic.setClinicName("Test Clinic");

        // Set up test provider
        testProvider = new Provider();
        testProvider.setProviderNo("999998");
        testProvider.setFirstName("Test");
        testProvider.setLastName("Provider");

        // Set up LoggedInInfo
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProvider()).thenReturn(testProvider);
    }

    @Test
    @DisplayName("Generate encrypted URL when valid configuration is provided")
    void shouldGenerateEncryptedURL_whenValidConfiguration() {
        try (MockedStatic<OscarProperties> mockedProperties = mockStatic(OscarProperties.class)) {
            OscarProperties properties = mock(OscarProperties.class);
            mockedProperties.when(OscarProperties::getInstance).thenReturn(properties);

            when(properties.getProperty("shared_outcomes_dashboard_url"))
                    .thenReturn("https://test.dashboard.example.com");
            when(properties.getProperty("shared_outcomes_dashboard_key"))
                    .thenReturn("test-password-for-encryption");
            when(properties.getProperty("shared_outcomes_dashboard_salt"))
                    .thenReturn("8f3c21ab56789def1234567890abcdef");
            when(properties.getProperty("shared_outcomes_dashboard_clinic_id"))
                    .thenReturn("TEST_CLINIC_001");

            when(clinicDAO.getClinic()).thenReturn(testClinic);

            String url = dashboardManager.getSharedOutcomesDashboardLaunchURL(loggedInInfo);

            assertThat(url).isNotNull();
            assertThat(url).startsWith("https://test.dashboard.example.com");
            assertThat(url).contains("encodedParams=");
            assertThat(url).contains("version=1.1");
        }
    }

    @Test
    @DisplayName("Produce valid Base64 output when encrypting parameters")
    void shouldProduceValidBase64Output_whenEncrypting() {
        try (MockedStatic<OscarProperties> mockedProperties = mockStatic(OscarProperties.class)) {
            OscarProperties properties = mock(OscarProperties.class);
            mockedProperties.when(OscarProperties::getInstance).thenReturn(properties);

            when(properties.getProperty("shared_outcomes_dashboard_url"))
                    .thenReturn("https://test.dashboard.example.com");
            when(properties.getProperty("shared_outcomes_dashboard_key"))
                    .thenReturn("test-password");
            when(properties.getProperty("shared_outcomes_dashboard_salt"))
                    .thenReturn("8f3c21ab56789def1234567890abcdef");
            when(properties.getProperty("shared_outcomes_dashboard_clinic_id"))
                    .thenReturn("TEST_CLINIC");

            when(clinicDAO.getClinic()).thenReturn(testClinic);

            String url = dashboardManager.getSharedOutcomesDashboardLaunchURL(loggedInInfo);

            assertThat(url).isNotNull();

            // Extract encoded params from URL
            String encodedParams = url.substring(url.indexOf("encodedParams=") + 14, url.indexOf("&version"));

            // Verify it's valid Base64
            assertThat(encodedParams).isNotEmpty();
            assertThat(encodedParams).matches("^[A-Za-z0-9+/=]+$");

            // Verify we can decode it
            byte[] decoded = Base64.getDecoder().decode(encodedParams);
            assertThat(decoded).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Produce different output when called multiple times (random IV)")
    void shouldProduceDifferentOutput_whenCalledMultipleTimes() {
        try (MockedStatic<OscarProperties> mockedProperties = mockStatic(OscarProperties.class)) {
            OscarProperties properties = mock(OscarProperties.class);
            mockedProperties.when(OscarProperties::getInstance).thenReturn(properties);

            when(properties.getProperty("shared_outcomes_dashboard_url"))
                    .thenReturn("https://test.dashboard.example.com");
            when(properties.getProperty("shared_outcomes_dashboard_key"))
                    .thenReturn("test-password");
            when(properties.getProperty("shared_outcomes_dashboard_salt"))
                    .thenReturn("8f3c21ab56789def1234567890abcdef");
            when(properties.getProperty("shared_outcomes_dashboard_clinic_id"))
                    .thenReturn("TEST_CLINIC");

            when(clinicDAO.getClinic()).thenReturn(testClinic);

            String url1 = dashboardManager.getSharedOutcomesDashboardLaunchURL(loggedInInfo);
            String url2 = dashboardManager.getSharedOutcomesDashboardLaunchURL(loggedInInfo);

            assertThat(url1).isNotNull();
            assertThat(url2).isNotNull();

            // URLs should be different due to random IV in encryption
            assertThat(url1).isNotEqualTo(url2);
        }
    }

    @Test
    @DisplayName("Return null when password configuration is missing")
    void shouldReturnNull_whenMissingPassword() {
        try (MockedStatic<OscarProperties> mockedProperties = mockStatic(OscarProperties.class)) {
            OscarProperties properties = mock(OscarProperties.class);
            mockedProperties.when(OscarProperties::getInstance).thenReturn(properties);

            when(properties.getProperty("shared_outcomes_dashboard_url"))
                    .thenReturn("https://test.dashboard.example.com");
            when(properties.getProperty("shared_outcomes_dashboard_key"))
                    .thenReturn(null);  // Missing password
            when(properties.getProperty("shared_outcomes_dashboard_salt"))
                    .thenReturn("8f3c21ab56789def1234567890abcdef");

            when(clinicDAO.getClinic()).thenReturn(testClinic);

            String url = dashboardManager.getSharedOutcomesDashboardLaunchURL(loggedInInfo);

            // Should return null when encryption fails
            assertThat(url).isNull();
        }
    }

    @Test
    @DisplayName("Return null when salt configuration is missing")
    void shouldReturnNull_whenMissingSalt() {
        try (MockedStatic<OscarProperties> mockedProperties = mockStatic(OscarProperties.class)) {
            OscarProperties properties = mock(OscarProperties.class);
            mockedProperties.when(OscarProperties::getInstance).thenReturn(properties);

            when(properties.getProperty("shared_outcomes_dashboard_url"))
                    .thenReturn("https://test.dashboard.example.com");
            when(properties.getProperty("shared_outcomes_dashboard_key"))
                    .thenReturn("test-password");
            when(properties.getProperty("shared_outcomes_dashboard_salt"))
                    .thenReturn(null);  // Missing salt

            when(clinicDAO.getClinic()).thenReturn(testClinic);

            String url = dashboardManager.getSharedOutcomesDashboardLaunchURL(loggedInInfo);

            // Should return null when encryption fails
            assertThat(url).isNull();
        }
    }

    @Test
    @DisplayName("Decrypt correctly using Spring Security Crypto (round-trip test)")
    void shouldDecryptCorrectly_whenUsingSpringSecurityCrypto() {
        String testJson = "{\"clinic\":{\"name\":\"Test Clinic\"},\"user\":{\"username\":\"test\"}}";
        String password = "test-password";
        String salt = "8f3c21ab56789def1234567890abcdef";

        // Encrypt
        BytesEncryptor encryptor = Encryptors.stronger(password, salt);
        byte[] encrypted = encryptor.encrypt(testJson.getBytes(StandardCharsets.UTF_8));
        String base64Encoded = Base64.getEncoder().encodeToString(encrypted);

        // Decrypt
        byte[] decoded = Base64.getDecoder().decode(base64Encoded);
        byte[] decrypted = encryptor.decrypt(decoded);
        String decryptedJson = new String(decrypted, StandardCharsets.UTF_8);

        // Verify round-trip
        assertThat(decryptedJson).isEqualTo(testJson);
    }

    @Test
    @DisplayName("Return null when dashboard URL configuration is missing")
    void shouldReturnNull_whenMissingDashboardURL() {
        try (MockedStatic<OscarProperties> mockedProperties = mockStatic(OscarProperties.class)) {
            OscarProperties properties = mock(OscarProperties.class);
            mockedProperties.when(OscarProperties::getInstance).thenReturn(properties);

            when(properties.getProperty("shared_outcomes_dashboard_url"))
                    .thenReturn(null);  // Missing URL

            String url = dashboardManager.getSharedOutcomesDashboardLaunchURL(loggedInInfo);

            assertThat(url).isNull();
        }
    }
}
