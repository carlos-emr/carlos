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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pre-flight guards of {@link MiddlewareFaxProviderClient#cancelFax}.
 *
 * <p>cancelFax validates in this order: provider-type match, presence of a provider jobId,
 * then middleware connection configuration — all before any HTTP connection is attempted, so
 * these paths are testable without network infrastructure. The actual PUT transport is covered
 * by the shared hardened client path exercised elsewhere.</p>
 *
 * @since 2026-08-21
 * @see MiddlewareFaxProviderClient
 * @see MiddlewareFaxProviderClientValidationTest
 */
@Tag("unit")
@Tag("fax")
@Tag("middleware")
@DisplayName("MiddlewareFaxProviderClient cancelFax pre-flight guards")
class MiddlewareFaxProviderClientCancelUnitTest extends CarlosUnitTestBase {

    private MiddlewareFaxProviderClient client;
    private FaxJob jobWithProviderId;

    @BeforeEach
    void setUp() {
        client = new MiddlewareFaxProviderClient();
        jobWithProviderId = new FaxJob();
        jobWithProviderId.setJobId(424242L);
    }

    @Test
    @DisplayName("should throw FaxProviderException when middleware URL is missing")
    void shouldThrowFaxProviderException_whenUrlMissing() {
        FaxConfig config = createConfig(null, "siteUser", "faxUser", "sitePassword", "faxPassword");

        assertThatThrownBy(() -> client.cancelFax(config, jobWithProviderId))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("URL")
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("should throw FaxProviderException when site user is missing")
    void shouldThrowFaxProviderException_whenSiteUserMissing() {
        FaxConfig config = createConfig("https://203.0.113.10", null, "faxUser", "sitePassword", "faxPassword");

        assertThatThrownBy(() -> client.cancelFax(config, jobWithProviderId))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("site user")
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("should throw FaxProviderException when site password is missing")
    void shouldThrowFaxProviderException_whenSitePasswordMissing() {
        FaxConfig config = createConfig("https://203.0.113.10", "siteUser", "faxUser", null, "faxPassword");

        assertThatThrownBy(() -> client.cancelFax(config, jobWithProviderId))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("site password")
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("should throw FaxProviderException when fax user is missing")
    void shouldThrowFaxProviderException_whenFaxUserMissing() {
        FaxConfig config = createConfig("https://203.0.113.10", "siteUser", null, "sitePassword", "faxPassword");

        assertThatThrownBy(() -> client.cancelFax(config, jobWithProviderId))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("fax user")
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("should throw FaxProviderException when fax password is missing")
    void shouldThrowFaxProviderException_whenFaxPasswordMissing() {
        FaxConfig config = createConfig("https://203.0.113.10", "siteUser", "faxUser", "sitePassword", null);

        assertThatThrownBy(() -> client.cancelFax(config, jobWithProviderId))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("fax password")
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when config is for the SRFAX provider")
    void shouldThrowIllegalArgumentException_whenConfigIsSrfaxProvider() {
        FaxConfig srfaxConfig = mock(FaxConfig.class);
        when(srfaxConfig.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);

        assertThatThrownBy(() -> client.cancelFax(srfaxConfig, jobWithProviderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MIDDLEWARE")
                .hasMessageContaining("SRFAX");
    }

    @Test
    @DisplayName("should throw FaxProviderException when the job has no provider job id")
    void shouldThrowFaxProviderException_whenJobIdMissing() {
        // Given - a fully valid config: the jobId guard fires before config validation and
        // before any connection attempt
        FaxConfig config = createConfig("https://203.0.113.10", "siteUser", "faxUser", "sitePassword", "faxPassword");
        FaxJob jobWithoutProviderId = new FaxJob();

        assertThatThrownBy(() -> client.cancelFax(config, jobWithoutProviderId))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("no provider job id");
    }

    // -- helper methods --

    /**
     * Creates a mocked FaxConfig with full control over all middleware connection parameters.
     * Mocking avoids the EncryptionUtils dependency in getPasswd()/getFaxPasswd(). The URL uses
     * a literal public IP (RFC 5737 TEST-NET-3) so endpoint validation needs no DNS lookup.
     */
    private FaxConfig createConfig(String url, String siteUser, String faxUser, String passwd, String faxPasswd) {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getUrl()).thenReturn(url);
        when(config.getSiteUser()).thenReturn(siteUser);
        when(config.getFaxUser()).thenReturn(faxUser);
        when(config.getPasswd()).thenReturn(passwd);
        when(config.getFaxPasswd()).thenReturn(faxPasswd);
        when(config.getProviderType()).thenReturn(FaxConfig.ProviderType.MIDDLEWARE);
        return config;
    }
}
