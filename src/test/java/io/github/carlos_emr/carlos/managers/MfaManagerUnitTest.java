/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MfaManagerImpl} Multi-Factor Authentication logic.
 *
 * <p>Tests TOTP URL generation, MFA registration checks, secret management
 * (save, get, reset), and QR code data generation.</p>
 *
 * @since 2026-03-31
 * @see MfaManagerImpl
 * @see MfaManager
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MfaManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("security")
@Tag("mfa")
class MfaManagerUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityDao mockSecurityDao;
    @Mock private SecurityManager mockSecurityManager;

    private MfaManagerImpl manager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        registerMock(SecurityDao.class, mockSecurityDao);
        registerMock(SecurityManager.class, mockSecurityManager);

        manager = new MfaManagerImpl(mockSecurityDao, mockSecurityManager);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    // -----------------------------------------------------------------------
    // getTotpUrl
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getTotpUrl")
    class GetTotpUrl {

        @Test
        @DisplayName("should generate valid TOTP URL with encoded parameters")
        void shouldGenerateValidTotpUrl_withEncodedParams() throws UnsupportedEncodingException {
            String result = manager.getTotpUrl("CARLOS EMR", "admin", "JBSWY3DPEHPK3PXP");

            assertThat(result).startsWith("otpauth://totp/");
            assertThat(result).contains("CARLOS%20EMR");
            assertThat(result).contains("admin");
            assertThat(result).contains("secret=JBSWY3DPEHPK3PXP");
            assertThat(result).contains("issuer=CARLOS%20EMR");
        }

        @Test
        @DisplayName("should URL-encode special characters in app name")
        void shouldUrlEncode_specialCharsInAppName() throws UnsupportedEncodingException {
            String result = manager.getTotpUrl("My Clinic & Lab", "user@test.com", "SECRET");

            assertThat(result).contains("My%20Clinic");
            assertThat(result).doesNotContain(" ");
        }
    }

    // -----------------------------------------------------------------------
    // isMfaRegistrationRequired
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("isMfaRegistrationRequired")
    class IsMfaRegistrationRequired {

        @Test
        @DisplayName("should return true when security record needs MFA registration")
        void shouldReturnTrue_whenMfaRegistrationNeeded() {
            Security sec = new Security();
            sec.setId(1);
            sec.setMfaSecret(null); // no MFA secret set = registration needed
            when(mockSecurityDao.find(1)).thenReturn(sec);

            boolean result = manager.isMfaRegistrationRequired(1);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should throw IllegalStateException when security record not found")
        void shouldThrow_whenSecurityNotFound() {
            when(mockSecurityDao.find(999)).thenReturn(null);

            assertThatThrownBy(() -> manager.isMfaRegistrationRequired(999))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Security not found");
        }
    }

    // -----------------------------------------------------------------------
    // saveMfaSecret
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("saveMfaSecret")
    class SaveMfaSecret {

        @Test
        @DisplayName("should encrypt and save MFA secret via SecurityManager")
        void shouldEncryptAndSave_mfaSecret() throws Exception {
            Security sec = new Security();
            sec.setId(1);
            sec.setProviderNo("111");
            sec.setUserName("admin");
            sec.setPassword("hash");

            try (MockedStatic<EncryptionUtils> encMock = mockStatic(EncryptionUtils.class)) {
                encMock.when(() -> EncryptionUtils.encrypt("MYSECRET")).thenReturn("ENCRYPTED");
                // Allow other EncryptionUtils calls to pass through
                encMock.when(() -> EncryptionUtils.verify(any(), any())).thenReturn(true);
                encMock.when(() -> EncryptionUtils.isPasswordHashUpgradeNeeded(any())).thenReturn(false);

                manager.saveMfaSecret(loggedInInfo, sec, "MYSECRET");
            }

            assertThat(sec.getMfaSecret()).isEqualTo("ENCRYPTED");
            verify(mockSecurityManager).updateSecurityRecord(loggedInInfo, sec);
        }
    }

    // -----------------------------------------------------------------------
    // getMfaSecret
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getMfaSecret")
    class GetMfaSecret {

        @Test
        @DisplayName("should decrypt and return MFA secret")
        void shouldDecryptAndReturn_mfaSecret() throws Exception {
            Security sec = new Security();
            sec.setMfaSecret("ENCRYPTED_DATA");

            try (MockedStatic<EncryptionUtils> encMock = mockStatic(EncryptionUtils.class)) {
                encMock.when(() -> EncryptionUtils.decrypt("ENCRYPTED_DATA")).thenReturn("DECRYPTED_SECRET");

                String result = manager.getMfaSecret(sec);

                assertThat(result).isEqualTo("DECRYPTED_SECRET");
            }
        }
    }

    // -----------------------------------------------------------------------
    // resetMfaSecret
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("resetMfaSecret")
    class ResetMfaSecret {

        @Test
        @DisplayName("should set MFA secret to null and update via SecurityManager")
        void shouldClearSecret_andUpdate() {
            Security sec = new Security();
            sec.setId(1);
            sec.setProviderNo("111");
            sec.setUserName("admin");
            sec.setPassword("hash");
            sec.setMfaSecret("SOME_SECRET");

            manager.resetMfaSecret(loggedInInfo, sec);

            assertThat(sec.getMfaSecret()).isNull();
            verify(mockSecurityManager).updateSecurityRecord(loggedInInfo, sec);
        }
    }

    // -----------------------------------------------------------------------
    // getQRCodeImageData(Integer securityId, String secret)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getQRCodeImageData by securityId")
    class GetQRCodeImageDataBySecurityId {

        @Test
        @DisplayName("should return null when security record not found")
        void shouldReturnNull_whenSecurityNotFound() {
            when(mockSecurityDao.find(999)).thenReturn(null);

            String result = manager.getQRCodeImageData(999, "SECRET");

            assertThat(result).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // MfaManager static methods
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("generateMfaSecret static")
    class GenerateMfaSecretStatic {

        @Test
        @DisplayName("should generate non-null Base32 encoded secret")
        void shouldGenerateNonNullBase32Secret() {
            String secret = MfaManager.generateMfaSecret();

            assertThat(secret).isNotNull();
            assertThat(secret).isNotEmpty();
            // Base32 characters only
            assertThat(secret).matches("[A-Z2-7=]+");
        }

        @Test
        @DisplayName("should generate unique secrets on each call")
        void shouldGenerateUniqueSecrets() {
            String secret1 = MfaManager.generateMfaSecret();
            String secret2 = MfaManager.generateMfaSecret();

            assertThat(secret1).isNotEqualTo(secret2);
        }
    }
}
