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
package io.github.carlos_emr.carlos.fax.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;

/**
 * SSRF prevention tests for {@link SRFaxProviderClient#getSrfaxApiUrl()}.
 *
 * <p>Verifies that the SSRF domain allowlist correctly restricts the configurable
 * SRFax API URL to official srfax.com HTTPS endpoints only. Malicious or misconfigured
 * URLs must fall back to the default API endpoint.</p>
 *
 * @since 2026-02-19
 */
@Tag("unit")
@Tag("fax")
@Tag("security")
@DisplayName("SRFaxProviderClient SSRF Prevention Tests")
class SRFaxProviderClientSsrfTest extends CarlosUnitTestBase {

    private SRFaxProviderClient client;
    private Method getSrfaxApiUrlMethod;
    private MockedStatic<OscarProperties> oscarPropertiesMock;
    private OscarProperties mockProperties;

    @BeforeEach
    void setUp() throws Exception {
        client = new SRFaxProviderClient();

        getSrfaxApiUrlMethod = SRFaxProviderClient.class.getDeclaredMethod("getSrfaxApiUrl");
        getSrfaxApiUrlMethod.setAccessible(true);

        mockProperties = mock(OscarProperties.class);
        oscarPropertiesMock = Mockito.mockStatic(OscarProperties.class);
        oscarPropertiesMock.when(OscarProperties::getInstance).thenReturn(mockProperties);
    }

    @AfterEach
    void tearDown() {
        if (oscarPropertiesMock != null) {
            oscarPropertiesMock.close();
        }
    }

    private String invokeGetSrfaxApiUrl() throws Exception {
        return (String) getSrfaxApiUrlMethod.invoke(client);
    }

    @Nested
    @DisplayName("Valid URLs (should be accepted)")
    class ValidUrls {

        @Test
        @DisplayName("should return default URL when no property is configured")
        void shouldReturnDefault_whenPropertyNotConfigured() throws Exception {
            when(mockProperties.getProperty("srfax.api.url")).thenReturn(null);

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should return default URL when property is empty")
        void shouldReturnDefault_whenPropertyIsEmpty() throws Exception {
            when(mockProperties.getProperty("srfax.api.url")).thenReturn("");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should accept official SRFax HTTPS URL")
        void shouldAccept_officialSrfaxHttpsUrl() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://www.srfax.com/SRF_SecWebSvc.php");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo("https://www.srfax.com/SRF_SecWebSvc.php");
        }

        @Test
        @DisplayName("should accept bare srfax.com HTTPS domain")
        void shouldAccept_bareSrfaxDomain() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://srfax.com/api");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo("https://srfax.com/api");
        }

        @Test
        @DisplayName("should accept srfax.com subdomain HTTPS URL")
        void shouldAccept_srfaxSubdomain() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://api.srfax.com/v2/fax");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo("https://api.srfax.com/v2/fax");
        }

        @Test
        @DisplayName("should trim whitespace from configured URL")
        void shouldTrimWhitespace() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("  https://www.srfax.com/SRF_SecWebSvc.php  ");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo("https://www.srfax.com/SRF_SecWebSvc.php");
        }
    }

    @Nested
    @DisplayName("SSRF attacks (should be rejected)")
    class SsrfAttacks {

        @Test
        @DisplayName("should reject HTTP (non-HTTPS) srfax.com URL")
        void shouldReject_httpSrfaxUrl() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("http://www.srfax.com/SRF_SecWebSvc.php");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject attacker-controlled domain")
        void shouldReject_attackerDomain() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://evil.com/steal-creds");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject domain suffix attack (evil-srfax.com)")
        void shouldReject_domainSuffixAttack() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://evil-srfax.com/api");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject domain suffix attack (notsrfax.com)")
        void shouldReject_domainPrefixSuffixAttack() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://notsrfax.com/api");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject internal network URL (localhost)")
        void shouldReject_localhost() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://localhost:8080/steal");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject internal network URL (192.168.x.x)")
        void shouldReject_internalIp() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://192.168.1.100/api");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject credential-in-URL attack (user:pass@evil.com)")
        void shouldReject_credentialInUrl() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://user:pass@evil.com/api");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject FTP scheme")
        void shouldReject_ftpScheme() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("ftp://srfax.com/files");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject JavaScript scheme")
        void shouldReject_javascriptScheme() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("javascript:alert(1)");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject malformed URI and return default")
        void shouldReject_malformedUri() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("ht tp://not a valid uri");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject srfax.com.evil.com subdomain hijack")
        void shouldReject_subdomainHijack() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("https://srfax.com.evil.com/api");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }

        @Test
        @DisplayName("should reject file scheme")
        void shouldReject_fileScheme() throws Exception {
            when(mockProperties.getProperty("srfax.api.url"))
                    .thenReturn("file:///etc/passwd");

            assertThat(invokeGetSrfaxApiUrl())
                    .isEqualTo(SRFaxProviderClient.DEFAULT_SRFAX_API_URL);
        }
    }
}
