/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.all.util;

import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.model.PublicKey;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link KeyPairGen}.
 *
 * <p>Verifies that RSA key generation meets NIST minimum key size requirements
 * (2048-bit minimum per NIST SP 800-131A). This is a regression guard against
 * CodeQL alerts java/TooSmallRsaKeySizeUsed and java/insufficient-key-size.</p>
 *
 * @since 2026-04-08
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
@DisplayName("KeyPairGen")
class KeyPairGenUnitTest extends CarlosUnitTestBase {

    @Mock
    private PublicKeyDao mockPublicKeyDao;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registerMock(PublicKeyDao.class, mockPublicKeyDao);
    }

    @Test
    @DisplayName("should generate RSA keys with at least 2048-bit modulus")
    void shouldGenerateRsaKeys_withAtLeast2048BitModulus() throws Exception {
        // When: create keys for a non-oscar service (avoids OscarKeyDao dependency)
        String result = KeyPairGen.createKeys("testservice", "CML");

        // Then: key creation should succeed
        assertThat(result).isNotNull();

        // Capture the persisted PublicKey to inspect key size
        ArgumentCaptor<PublicKey> captor = ArgumentCaptor.forClass(PublicKey.class);
        verify(mockPublicKeyDao).persist(captor.capture());

        PublicKey savedKey = captor.getValue();
        assertThat(savedKey.getBase64EncodedPublicKey()).isNotBlank();

        // Decode the public key and verify its RSA modulus length
        // Use MIME decoder because Apache Commons Codec Base64 may include line breaks
        byte[] keyBytes = Base64.getMimeDecoder().decode(savedKey.getBase64EncodedPublicKey());
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        RSAPublicKey rsaKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);

        assertThat(rsaKey.getModulus().bitLength())
                .as("RSA key modulus must be at least 2048 bits per NIST SP 800-131A")
                .isGreaterThanOrEqualTo(2048);
    }
}
