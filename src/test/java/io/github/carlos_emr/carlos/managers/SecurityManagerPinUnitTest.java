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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("SecurityManager PIN hashing")
class SecurityManagerPinUnitTest {

    private static final String RAW_PIN = "1234";
    private static final Integer SECURITY_NO = 4242;

    private AutoCloseable mockitoCloseable;
    private SecurityManager securityManager;

    @Mock private SecurityDao securityDao;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        securityManager = new SecurityManager();
        ReflectionTestUtils.setField(securityManager, "securityDao", securityDao);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    private Security securityWithPin(String storedPin) {
        Security security = new Security();
        security.setSecurityNo(SECURITY_NO);
        security.setPin(storedPin);
        return security;
    }

    /** Lets the compare-and-set succeed, as it would against an unchanged row. */
    private void allowConditionalPinUpdate() {
        when(securityDao.updatePinHashIfUnchanged(anyInt(), anyString(), anyString(), any(Date.class)))
                .thenReturn(1);
    }

    private void verifyNoPinWrite() {
        verify(securityDao, never()).merge(any(Security.class));
        verify(securityDao, never())
                .updatePinHashIfUnchanged(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should validate hashed PIN without persisting upgrade")
    void shouldValidateHashedPin_withoutPersistingUpgrade() {
        Security security = securityWithPin(securityManager.encodePin(RAW_PIN));

        boolean valid = securityManager.validatePin(RAW_PIN, security);

        assertThat(valid).isTrue();
        verifyNoPinWrite();
    }

    @Test
    @DisplayName("should skip persisting hashed PIN when upgrade is not needed")
    void shouldSkipPersistingHashedPin_whenUpgradeIsNotNeeded() {
        Security security = securityWithPin(securityManager.encodePin(RAW_PIN));

        boolean upgraded = securityManager.upgradePinHashIfNeeded(RAW_PIN, security);

        assertThat(upgraded).isTrue();
        verifyNoPinWrite();
    }

    @Test
    @DisplayName("should validate legacy plaintext PIN without persisting upgrade")
    void shouldValidateLegacyPlaintextPin_withoutPersistingUpgrade() {
        Security security = securityWithPin(RAW_PIN);

        boolean valid = securityManager.validatePin(RAW_PIN, security);

        assertThat(valid).isTrue();
        assertThat(security.getPin()).isEqualTo(RAW_PIN);
        verifyNoPinWrite();
    }

    @Test
    @DisplayName("should upgrade legacy plaintext PIN when upgrade is requested")
    void shouldUpgradeLegacyPlaintextPin_whenUpgradeRequested() {
        Security security = securityWithPin(RAW_PIN);
        allowConditionalPinUpdate();

        boolean upgraded = securityManager.upgradePinHashIfNeeded(RAW_PIN, security);

        assertThat(upgraded).isTrue();
        verify(securityDao).updatePinHashIfUnchanged(
                eq(SECURITY_NO), eq(RAW_PIN), anyString(), any(Date.class));
        assertThat(security.getPin()).isNotEqualTo(RAW_PIN);
        assertThat(securityManager.validatePin(RAW_PIN, security)).isTrue();
        assertThat(security.getPinUpdateDate()).isNotNull();
    }

    @Test
    @DisplayName("should validate legacy encrypted PIN without persisting upgrade")
    void shouldValidateLegacyEncryptedPin_withoutPersistingUpgrade() {
        String legacyEncryptedPin = Misc.encryptPIN(RAW_PIN);
        Security security = securityWithPin(legacyEncryptedPin);

        boolean valid = securityManager.validatePin(RAW_PIN, security);

        assertThat(valid).isTrue();
        assertThat(security.getPin()).isEqualTo(legacyEncryptedPin);
        verifyNoPinWrite();
    }

    @Test
    @DisplayName("should upgrade legacy encrypted PIN when upgrade is requested")
    void shouldUpgradeLegacyEncryptedPin_whenUpgradeRequested() {
        String legacyEncryptedPin = Misc.encryptPIN(RAW_PIN);
        Security security = securityWithPin(legacyEncryptedPin);
        allowConditionalPinUpdate();

        boolean upgraded = securityManager.upgradePinHashIfNeeded(RAW_PIN, security);

        assertThat(upgraded).isTrue();
        // The compare-and-set must be pinned to the legacy value that was actually validated.
        verify(securityDao).updatePinHashIfUnchanged(
                eq(SECURITY_NO), eq(legacyEncryptedPin), anyString(), any(Date.class));
        assertThat(security.getPin()).isNotEqualTo(legacyEncryptedPin);
        assertThat(security.getPin()).startsWith("{");
        assertThat(securityManager.validatePin(RAW_PIN, security)).isTrue();
        assertThat(security.getPinUpdateDate()).isNotNull();
    }

    @Test
    @DisplayName("should reject wrong legacy PIN without persisting upgrade")
    void shouldRejectWrongLegacyPin_withoutPersistingUpgrade() {
        Security security = securityWithPin(RAW_PIN);

        boolean valid = securityManager.validatePin("9999", security);

        assertThat(valid).isFalse();
        assertThat(security.getPin()).isEqualTo(RAW_PIN);
        verifyNoPinWrite();
    }

    @Test
    @DisplayName("should leave stored PIN untouched when it changed since authentication")
    void shouldLeaveStoredPinUntouched_whenItChangedSinceAuthentication() {
        Security security = securityWithPin(RAW_PIN);
        // 0 rows updated is how the compare-and-set reports that the row no longer holds the
        // value this login validated against.
        when(securityDao.updatePinHashIfUnchanged(anyInt(), anyString(), anyString(), any(Date.class)))
                .thenReturn(0);

        boolean upgraded = securityManager.upgradePinHashIfNeeded(RAW_PIN, security);

        // Losing the race is not a failure to report: the other writer's value is the newer one.
        assertThat(upgraded).isTrue();
        assertThat(security.getPin()).isEqualTo(RAW_PIN);
        verify(securityDao, never()).merge(any(Security.class));
    }

    @Test
    @DisplayName("should reject malformed stored hash without throwing")
    void shouldRejectMalformedStoredHash_withoutThrowing() {
        // A tagged but unparseable value must read as "no match", not blow up the login path.
        Security security = securityWithPin("{bcrypt}not-a-real-hash");

        boolean valid = securityManager.validatePin(RAW_PIN, security);

        assertThat(valid).isFalse();
        verifyNoPinWrite();
    }

    @Test
    @DisplayName("should report upgrade needed for untagged legacy PIN")
    void shouldReportUpgradeNeeded_forUntaggedLegacyPin() {
        assertThat(securityManager.isPinHashUpgradeNeeded(securityWithPin(RAW_PIN))).isTrue();
        assertThat(securityManager.isPinHashUpgradeNeeded(securityWithPin(Misc.encryptPIN(RAW_PIN)))).isTrue();
        assertThat(securityManager.isPinHashUpgradeNeeded(securityWithPin(securityManager.encodePin(RAW_PIN))))
                .isFalse();
        assertThat(securityManager.isPinHashUpgradeNeeded(securityWithPin(null))).isFalse();
    }
}
