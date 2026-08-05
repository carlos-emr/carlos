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

import io.github.carlos_emr.carlos.commn.dao.DigitalSignatureDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DigitalSignatureManagerImpl#getDigitalSignature(int)} decrypt-failure
 * behavior.
 *
 * <p>Pins the magic-byte fallback: when {@link EncryptionUtils#decrypt(byte[])} fails the entity is
 * never re-encrypted/persisted (the old destructive double-encrypt bug), and the stored bytes are
 * only returned when they really look like a raster image — otherwise the image is nulled so the
 * signature servlet 404s instead of streaming ciphertext as image/jpeg.</p>
 */
@Tag("unit")
@Tag("manager")
@DisplayName("DigitalSignatureManagerImpl.getDigitalSignature decrypt-failure fallback")
class DigitalSignatureManagerImplUnitTest {

    private static final int SIGNATURE_ID = 42;

    // Minimal valid PNG magic prefix (89 50 4E 47) padded past the 4-byte sniff window.
    private static final byte[] PNG_MAGIC =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    @DisplayName("should keep stored bytes and never persist when decrypt fails but bytes are image magic")
    void shouldKeepStoredBytes_whenDecryptFailsButBytesAreImageMagic() {
        DigitalSignatureDao dao = mock(DigitalSignatureDao.class);
        DigitalSignature stored = new DigitalSignature();
        stored.setSignatureImage(PNG_MAGIC);
        when(dao.findDetached(SIGNATURE_ID)).thenReturn(stored);

        DigitalSignatureManagerImpl manager = new DigitalSignatureManagerImpl(dao);

        try (MockedStatic<EncryptionUtils> encryption = mockStatic(EncryptionUtils.class)) {
            encryption.when(() -> EncryptionUtils.decrypt(any(byte[].class)))
                    .thenThrow(new RuntimeException("key unavailable"));

            DigitalSignature result = manager.getDigitalSignature(SIGNATURE_ID);

            // Legacy plaintext image: undecryptable, but the bytes look like a real PNG, so return
            // them unchanged rather than nulling a usable signature.
            assertThat(result).isNotNull();
            assertThat(result.getSignatureImage()).isEqualTo(PNG_MAGIC);
        }

        // The record must NOT be re-encrypted/persisted on the decrypt-failure path.
        verify(dao, never()).merge(any());
        verify(dao, never()).persist(any());
        verify(dao, never()).flush();
    }

    @Test
    @DisplayName("should null the image when decrypt fails and bytes are not image magic")
    void shouldNullImage_whenDecryptFailsAndBytesAreNotImageMagic() {
        DigitalSignatureDao dao = mock(DigitalSignatureDao.class);
        DigitalSignature stored = new DigitalSignature();
        // Undecryptable ciphertext that is NOT a known raster-image magic number.
        stored.setSignatureImage(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
        when(dao.findDetached(SIGNATURE_ID)).thenReturn(stored);

        DigitalSignatureManagerImpl manager = new DigitalSignatureManagerImpl(dao);

        try (MockedStatic<EncryptionUtils> encryption = mockStatic(EncryptionUtils.class)) {
            encryption.when(() -> EncryptionUtils.decrypt(any(byte[].class)))
                    .thenThrow(new RuntimeException("key unavailable"));

            DigitalSignature result = manager.getDigitalSignature(SIGNATURE_ID);

            // Genuinely-encrypted record whose key is unavailable: null the image so the render
            // fails honestly instead of streaming garbage as a valid image.
            assertThat(result).isNotNull();
            assertThat(result.getSignatureImage()).isNull();
        }

        verify(dao, never()).merge(any());
        verify(dao, never()).persist(any());
        verify(dao, never()).flush();
    }

    @Test
    @DisplayName("should return decrypted bytes when decrypt succeeds")
    void shouldReturnDecryptedBytes_whenDecryptSucceeds() {
        DigitalSignatureDao dao = mock(DigitalSignatureDao.class);
        byte[] ciphertext = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC};
        byte[] plaintext = {0x10, 0x20, 0x30};
        DigitalSignature stored = new DigitalSignature();
        stored.setSignatureImage(ciphertext);
        when(dao.findDetached(SIGNATURE_ID)).thenReturn(stored);

        DigitalSignatureManagerImpl manager = new DigitalSignatureManagerImpl(dao);

        try (MockedStatic<EncryptionUtils> encryption = mockStatic(EncryptionUtils.class)) {
            encryption.when(() -> EncryptionUtils.decrypt(any(byte[].class)))
                    .thenReturn(plaintext);

            DigitalSignature result = manager.getDigitalSignature(SIGNATURE_ID);

            assertThat(result).isNotNull();
            assertThat(result.getSignatureImage()).isEqualTo(plaintext);
        }
    }
}
