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

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@Tag("security")
@DisplayName("SecurityManager PIN hashing")
class SecurityManagerPinUnitTest {

    private static final String RAW_PIN = "1234";

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

    @Test
    @DisplayName("should validate hashed PIN without persisting upgrade")
    void shouldValidateHashedPin_withoutPersistingUpgrade() {
        Security security = new Security();
        security.setPin(securityManager.encodePin(RAW_PIN));

        boolean valid = securityManager.validatePin(RAW_PIN, security);

        assertThat(valid).isTrue();
        verify(securityDao, never()).merge(any(Security.class));
    }

    @Test
    @DisplayName("should upgrade legacy plaintext PIN when PIN matches")
    void shouldUpgradeLegacyPlaintextPin_whenPinMatches() {
        Security security = new Security();
        security.setPin(RAW_PIN);
        ArgumentCaptor<Security> securityCaptor = ArgumentCaptor.forClass(Security.class);

        boolean valid = securityManager.validatePin(RAW_PIN, security);

        assertThat(valid).isTrue();
        verify(securityDao).merge(securityCaptor.capture());
        assertThat(securityCaptor.getValue()).isSameAs(security);
        assertThat(security.getPin()).isNotEqualTo(RAW_PIN);
        assertThat(securityManager.validatePin(RAW_PIN, security)).isTrue();
        assertThat(security.getPinUpdateDate()).isNotNull();
    }

    @Test
    @DisplayName("should reject wrong legacy PIN without persisting upgrade")
    void shouldRejectWrongLegacyPin_withoutPersistingUpgrade() {
        Security security = new Security();
        security.setPin(RAW_PIN);

        boolean valid = securityManager.validatePin("9999", security);

        assertThat(valid).isFalse();
        assertThat(security.getPin()).isEqualTo(RAW_PIN);
        verify(securityDao, never()).merge(any(Security.class));
    }
}
