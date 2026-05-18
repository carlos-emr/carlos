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
package io.github.carlos_emr.carlos.fax.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConfigureFax2Action} RingCentral validation and field-application logic.
 *
 * <p>Drives {@code validateConfigRow} and {@code applyRingCentralFields} in isolation so the
 * provider-switch, masked-sentinel, and credential-rotation paths are pinned without booting a
 * Struts action context.</p>
 *
 * @since 2026-05-07
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("ConfigureFax2Action RingCentral Unit Tests")
class ConfigureFax2ActionRingCentralTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerSpringBeans() {
        // The static helpers under test do not depend on Spring beans, but other test classes
        // sharing CarlosUnitTestBase rely on these registrations being present, and the base
        // throws IllegalStateException if SpringUtils.getBean is called without a registered mock.
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(FaxManager.class, mock(FaxManager.class));
    }

    @Test
    @DisplayName("should reject new RingCentral row without client ID")
    void shouldReject_whenNewRingCentralRowMissingClientId() {
        String[] clientIds = { "" };
        String[] secrets = { "secret" };
        String[] jwts = { "jwt" };

        assertThatThrownBy(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.RINGCENTRAL,
                null, null, null,
                stringArray(""), stringArray(""), stringArray("4165551234"),
                stringArray("ops@example.com"), stringArray("1"),
                clientIds, secrets, jwts, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client ID");
    }

    @Test
    @DisplayName("should reject new RingCentral row without client secret")
    void shouldReject_whenNewRingCentralRowMissingClientSecret() {
        String[] clientIds = { "client" };
        String[] secrets = { "" };
        String[] jwts = { "jwt" };

        assertThatThrownBy(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.RINGCENTRAL,
                null, null, null,
                stringArray(""), stringArray(""), stringArray("4165551234"),
                stringArray("ops@example.com"), stringArray("1"),
                clientIds, secrets, jwts, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client secret");
    }

    @Test
    @DisplayName("should reject new RingCentral row when secret is the masked sentinel")
    void shouldReject_whenNewRingCentralRowSubmitsSentinelSecret() {
        // A new row has nothing to "preserve", so the mask sentinel is meaningless and would
        // otherwise persist the literal "**********" as the encrypted secret only to be caught
        // at @PrePersist with a generic save error.
        String[] clientIds = { "client" };
        String[] secrets = { ConfigureFax2Action.PASSWORD_MASK_SENTINEL };
        String[] jwts = { "jwt" };

        assertThatThrownBy(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.RINGCENTRAL,
                null, null, null,
                stringArray(""), stringArray(""), stringArray("4165551234"),
                stringArray("ops@example.com"), stringArray("1"),
                clientIds, secrets, jwts, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client secret");
    }

    @Test
    @DisplayName("should reject new RingCentral row when JWT is the masked sentinel")
    void shouldReject_whenNewRingCentralRowSubmitsSentinelJwt() {
        String[] clientIds = { "client" };
        String[] secrets = { "secret" };
        String[] jwts = { ConfigureFax2Action.PASSWORD_MASK_SENTINEL };

        assertThatThrownBy(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.RINGCENTRAL,
                null, null, null,
                stringArray(""), stringArray(""), stringArray("4165551234"),
                stringArray("ops@example.com"), stringArray("1"),
                clientIds, secrets, jwts, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT");
    }

    @Test
    @DisplayName("should reject existing RingCentral row when secret is cleared (blank, not sentinel)")
    void shouldReject_whenExistingRingCentralRowClearsSecret() {
        FaxConfig saved = new FaxConfig();
        saved.setRingCentralClientId("same-client");
        String[] clientIds = { "same-client" };
        String[] secrets = { "" };  // blank, not sentinel — would otherwise overwrite stored value
        String[] jwts = { ConfigureFax2Action.PASSWORD_MASK_SENTINEL };

        assertThatThrownBy(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.RINGCENTRAL,
                null, null, null,
                stringArray(""), stringArray(""), stringArray("4165551234"),
                stringArray("ops@example.com"), stringArray("1"),
                clientIds, secrets, jwts, 0, saved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be cleared");
    }

    @Test
    @DisplayName("should reject existing MIDDLEWARE row when site password is cleared")
    void shouldReject_whenExistingMiddlewareRowClearsSitePassword() {
        FaxConfig saved = new FaxConfig();
        assertThatThrownBy(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.MIDDLEWARE,
                "https://relay.example/", "user", "",
                stringArray("fax-user"), stringArray(ConfigureFax2Action.PASSWORD_MASK_SENTINEL),
                stringArray("4165551234"), stringArray("ops@example.com"), stringArray("1"),
                null, null, null, 0, saved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be cleared");
    }

    @Test
    @DisplayName("should reject existing SRFAX row when password is cleared")
    void shouldReject_whenExistingSrfaxRowClearsPassword() {
        FaxConfig saved = new FaxConfig();
        assertThatThrownBy(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.SRFAX,
                null, null, null,
                stringArray("fax-user"), stringArray(""),
                stringArray("4165551234"), stringArray("ops@example.com"), stringArray("1"),
                null, null, null, 0, saved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be cleared");
    }

    @Test
    @DisplayName("should reject new RingCentral row without JWT token")
    void shouldReject_whenNewRingCentralRowMissingJwt() {
        String[] clientIds = { "client" };
        String[] secrets = { "secret" };
        String[] jwts = { "" };

        assertThatThrownBy(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.RINGCENTRAL,
                null, null, null,
                stringArray(""), stringArray(""), stringArray("4165551234"),
                stringArray("ops@example.com"), stringArray("1"),
                clientIds, secrets, jwts, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT");
    }

    @Test
    @DisplayName("should accept existing RingCentral row with masked secrets when client ID is unchanged")
    void shouldAccept_whenExistingRowKeepsClientIdAndMasksSecrets() {
        FaxConfig saved = mock(FaxConfig.class);
        when(saved.getRingCentralClientId()).thenReturn("client");
        String[] clientIds = { "client" };
        String[] secrets = { ConfigureFax2Action.PASSWORD_MASK_SENTINEL };
        String[] jwts = { ConfigureFax2Action.PASSWORD_MASK_SENTINEL };

        assertThatCode(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.RINGCENTRAL,
                null, null, null,
                stringArray(""), stringArray(""), stringArray("4165551234"),
                stringArray("ops@example.com"), stringArray("1"),
                clientIds, secrets, jwts, 0, saved))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should require fresh secret and JWT when RingCentral client ID changes")
    void shouldRequireFreshSecretAndJwt_whenClientIdChanges() {
        FaxConfig saved = mock(FaxConfig.class);
        when(saved.getRingCentralClientId()).thenReturn("old-client");
        String[] clientIds = { "new-client" };
        String[] secrets = { ConfigureFax2Action.PASSWORD_MASK_SENTINEL };
        String[] jwts = { ConfigureFax2Action.PASSWORD_MASK_SENTINEL };

        assertThatThrownBy(() -> ConfigureFax2Action.validateConfigRow(
                FaxConfig.ProviderType.RINGCENTRAL,
                null, null, null,
                stringArray(""), stringArray(""), stringArray("4165551234"),
                stringArray("ops@example.com"), stringArray("1"),
                clientIds, secrets, jwts, 0, saved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client secret");
    }

    @Test
    @DisplayName("should detect RingCentral client ID change against saved config")
    void shouldDetectClientIdChange_againstSavedConfig() {
        FaxConfig saved = mock(FaxConfig.class);
        when(saved.getRingCentralClientId()).thenReturn("old-id");

        assertThat(ConfigureFax2Action.isRingCentralClientIdChanged(stringArray("new-id"), 0, saved)).isTrue();
        assertThat(ConfigureFax2Action.isRingCentralClientIdChanged(stringArray("old-id"), 0, saved)).isFalse();
        assertThat(ConfigureFax2Action.isRingCentralClientIdChanged(stringArray("new-id"), 0, null)).isFalse();
    }

    @Test
    @DisplayName("should clear RingCentral fields when provider switches away from RINGCENTRAL")
    void shouldClearFields_whenProviderSwitchesAwayFromRingCentral() {
        FaxConfig faxConfig = mock(FaxConfig.class);
        when(faxConfig.getRingCentralClientId()).thenReturn("had-credentials");
        String[] secrets = { "should-be-nulled" };
        String[] jwts = { "should-be-nulled" };

        ConfigureFax2Action.applyRingCentralFields(faxConfig, FaxConfig.ProviderType.MIDDLEWARE,
                stringArray(""), secrets, jwts, stringArray(""), stringArray(""), 0);

        verify(faxConfig).setRingCentralClientId("");
        verify(faxConfig).setRingCentralClientSecret("");
        verify(faxConfig).setRingCentralJwtToken("");
        verify(faxConfig).setRingCentralAccountId("");
        verify(faxConfig).setRingCentralExtensionId("");
        assertThat(secrets[0]).isNull();
        assertThat(jwts[0]).isNull();
    }

    @Test
    @DisplayName("should preserve stored secret when sentinel is submitted for unchanged RingCentral row")
    void shouldPreserveStoredSecret_whenSentinelSubmittedForUnchangedRow() {
        FaxConfig faxConfig = mock(FaxConfig.class);
        String[] clientIds = { "client" };
        String[] secrets = { ConfigureFax2Action.PASSWORD_MASK_SENTINEL };
        String[] jwts = { ConfigureFax2Action.PASSWORD_MASK_SENTINEL };

        ConfigureFax2Action.applyRingCentralFields(faxConfig, FaxConfig.ProviderType.RINGCENTRAL,
                clientIds, secrets, jwts, stringArray(""), stringArray(""), 0);

        verify(faxConfig).setRingCentralClientId("client");
        verify(faxConfig, never()).setRingCentralClientSecret(eq(ConfigureFax2Action.PASSWORD_MASK_SENTINEL));
        verify(faxConfig, never()).setRingCentralJwtToken(eq(ConfigureFax2Action.PASSWORD_MASK_SENTINEL));
    }

    @Test
    @DisplayName("should write fresh secret when non-sentinel value is submitted")
    void shouldWriteFreshSecret_whenNonSentinelValueIsSubmitted() {
        FaxConfig faxConfig = mock(FaxConfig.class);
        String[] clientIds = { "client" };
        String[] secrets = { "fresh-secret" };
        String[] jwts = { "fresh-jwt" };

        ConfigureFax2Action.applyRingCentralFields(faxConfig, FaxConfig.ProviderType.RINGCENTRAL,
                clientIds, secrets, jwts, stringArray(""), stringArray(""), 0);

        verify(faxConfig, times(1)).setRingCentralClientSecret("fresh-secret");
        verify(faxConfig, times(1)).setRingCentralJwtToken("fresh-jwt");
        assertThat(secrets[0]).isNull();
        assertThat(jwts[0]).isNull();
    }

    private static String[] stringArray(String value) {
        return new String[] { value };
    }
}
