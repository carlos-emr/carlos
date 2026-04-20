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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HashAudit}.
 *
 * @since 2026-04-17
 */
@Tag("unit")
@Tag("fast")
@DisplayName("HashAudit hashing")
class HashAuditUnitTest {

    @Test
    @DisplayName("should create SHA-256 signature when hashing note content")
    void shouldCreateSha256Signature_whenHashingNoteContent() throws Exception {
        HashAudit hashAudit = new HashAudit();
        byte[] input = "signed note content".getBytes(StandardCharsets.UTF_8);
        String expectedSignature = HexFormat.of()
                .formatHex(MessageDigest.getInstance(HashAudit.ALGORITHM).digest(input));

        hashAudit.makeHash(input);

        assertThat(hashAudit.getAlgorithm()).isEqualTo("SHA-256");
        assertThat(hashAudit.getSignature()).isEqualTo(expectedSignature);
        assertThat(hashAudit.getSignature()).hasSize(64);
    }

    @Test
    @DisplayName("should create SHA-256 signature when hashing empty input")
    void shouldCreateSha256Signature_whenHashingEmptyInput() throws Exception {
        HashAudit hashAudit = new HashAudit();
        byte[] input = new byte[0];
        String expectedSignature = HexFormat.of()
                .formatHex(MessageDigest.getInstance(HashAudit.ALGORITHM).digest(input));

        hashAudit.makeHash(input);

        assertThat(hashAudit.getSignature()).isEqualTo(expectedSignature);
        assertThat(hashAudit.getSignature()).hasSize(64);
    }
}
