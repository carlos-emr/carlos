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
package io.github.carlos_emr.carlos.commn.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.Base64;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FaxConfig} RingCentral credential encryption and persist invariants.
 *
 * <p>Verifies that RingCentral OAuth secrets are encrypted at rest, decrypt back to the original
 * plaintext, and that the {@code @PrePersist}/{@code @PreUpdate} hook rejects rows whose provider
 * type is RINGCENTRAL but whose required OAuth fields are missing.</p>
 *
 * @since 2026-05-07
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("FaxConfig RingCentral Encryption + Invariant Tests")
class FaxConfigRingCentralTest {

    @BeforeAll
    static void primeEncryptionKey() {
        // Provide a deterministic AES-128 key so EncryptionUtils.encrypt/decrypt can run without
        // depending on the deployment-time encryption.util.secret.key property.
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        CarlosProperties.getInstance().setProperty(
                EncryptionUtils.SECRET_KEY_ENV_VAR, Base64.getEncoder().encodeToString(key));
        EncryptionUtils.prepareSecretKeySpec();
    }

    @Test
    @DisplayName("should round-trip RingCentral client secret through set/get accessors")
    void shouldRoundTripClientSecret_throughEncryptingAccessors() {
        FaxConfig config = new FaxConfig();
        config.setRingCentralClientSecret("the-real-secret");

        assertThat(config.getRingCentralClientSecret()).isEqualTo("the-real-secret");
    }

    @Test
    @DisplayName("should round-trip RingCentral JWT token through set/get accessors")
    void shouldRoundTripJwtToken_throughEncryptingAccessors() {
        FaxConfig config = new FaxConfig();
        config.setRingCentralJwtToken("the-real-jwt");

        assertThat(config.getRingCentralJwtToken()).isEqualTo("the-real-jwt");
    }

    @Test
    @DisplayName("should store RingCentral client secret in encrypted form, not plaintext")
    void shouldStoreEncryptedClientSecret_notPlaintext() throws Exception {
        FaxConfig config = new FaxConfig();
        config.setRingCentralClientSecret("plaintext-secret");

        String stored = readPrivateField(config, "ringCentralClientSecret");
        assertThat(stored).isNotEqualTo("plaintext-secret");
        assertThat(EncryptionUtils.isEncrypted(stored)).isTrue();
    }

    @Test
    @DisplayName("should store RingCentral JWT token in encrypted form, not plaintext")
    void shouldStoreEncryptedJwtToken_notPlaintext() throws Exception {
        FaxConfig config = new FaxConfig();
        config.setRingCentralJwtToken("plaintext-jwt");

        String stored = readPrivateField(config, "ringCentralJwtToken");
        assertThat(stored).isNotEqualTo("plaintext-jwt");
        assertThat(EncryptionUtils.isEncrypted(stored)).isTrue();
    }

    @Test
    @DisplayName("should pass invariant when RingCentral OAuth fields are populated")
    void shouldPassInvariant_whenRingCentralFieldsArePopulated() {
        FaxConfig config = new FaxConfig();
        config.setProviderType(FaxConfig.ProviderType.RINGCENTRAL);
        config.setRingCentralClientId("client");
        config.setRingCentralClientSecret("secret");
        config.setRingCentralJwtToken("jwt");

        assertThatCode(config::assertProviderInvariants).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject persist when RingCentral provider has blank client ID")
    void shouldRejectPersist_whenRingCentralProviderHasBlankClientId() {
        FaxConfig config = new FaxConfig();
        config.setProviderType(FaxConfig.ProviderType.RINGCENTRAL);
        config.setRingCentralClientId("");
        config.setRingCentralClientSecret("secret");
        config.setRingCentralJwtToken("jwt");

        assertThatThrownBy(config::assertProviderInvariants)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ringCentralClientId");
    }

    @Test
    @DisplayName("should reject persist when RingCentral provider has blank client secret")
    void shouldRejectPersist_whenRingCentralProviderHasBlankClientSecret() {
        FaxConfig config = new FaxConfig();
        config.setProviderType(FaxConfig.ProviderType.RINGCENTRAL);
        config.setRingCentralClientId("client");
        config.setRingCentralJwtToken("jwt");
        // No client secret set

        assertThatThrownBy(config::assertProviderInvariants)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ringCentralClientSecret");
    }

    @Test
    @DisplayName("should reject persist when RingCentral provider has blank JWT token")
    void shouldRejectPersist_whenRingCentralProviderHasBlankJwtToken() {
        FaxConfig config = new FaxConfig();
        config.setProviderType(FaxConfig.ProviderType.RINGCENTRAL);
        config.setRingCentralClientId("client");
        config.setRingCentralClientSecret("secret");
        // No JWT token set

        assertThatThrownBy(config::assertProviderInvariants)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ringCentralJwtToken");
    }

    @Test
    @DisplayName("should not enforce RingCentral fields when provider is MIDDLEWARE with valid middleware fields")
    void shouldNotEnforceRingCentralFields_whenProviderIsMiddleware() {
        FaxConfig config = new FaxConfig();
        config.setProviderType(FaxConfig.ProviderType.MIDDLEWARE);
        config.setUrl("https://relay.example/");
        config.setSiteUser("site-user");
        config.setPasswd("site-pass");

        assertThatCode(config::assertProviderInvariants).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject persist when MIDDLEWARE provider has blank url")
    void shouldRejectPersist_whenMiddlewareProviderHasBlankUrl() {
        FaxConfig config = new FaxConfig();
        config.setProviderType(FaxConfig.ProviderType.MIDDLEWARE);
        config.setSiteUser("site-user");
        config.setPasswd("site-pass");

        assertThatThrownBy(config::assertProviderInvariants)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MIDDLEWARE")
                .hasMessageContaining("url");
    }

    @Test
    @DisplayName("should reject persist when SRFAX provider has blank fax user")
    void shouldRejectPersist_whenSrFaxProviderHasBlankFaxUser() {
        FaxConfig config = new FaxConfig();
        config.setProviderType(FaxConfig.ProviderType.SRFAX);
        config.setFaxPasswd("srfax-pass");

        assertThatThrownBy(config::assertProviderInvariants)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SRFAX")
                .hasMessageContaining("faxUser");
    }

    @Test
    @DisplayName("should accept SRFAX provider when fax user and password are populated")
    void shouldAcceptSrFaxProvider_whenFaxUserAndPasswordPopulated() {
        FaxConfig config = new FaxConfig();
        config.setProviderType(FaxConfig.ProviderType.SRFAX);
        config.setFaxUser("123456");
        config.setFaxPasswd("srfax-pass");

        assertThatCode(config::assertProviderInvariants).doesNotThrowAnyException();
    }

    private static String readPrivateField(FaxConfig config, String fieldName) throws Exception {
        Field field = FaxConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(config);
    }
}
