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
package io.github.carlos_emr.carlos.utility;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.github.carlos_emr.CarlosProperties;

/**
 * Computes the {@code document_id} of a {@link DocumentManifest}: an opaque ID that stays the same
 * for one CARLOS document across re-sends, without revealing its database row.
 *
 * <p>{@code document_id = b64url(HMAC-SHA256(secret, "carlos-emr/document-id/v1" 0x00
 * "<source>:<internal id>"))}, under the clinic secret {@value #SECRET_PROPERTY}. That secret is
 * generated once at setup and must never change after documents are issued: a new secret gives
 * every document a new ID. Without it, CARLOS signs PDFs but embeds no manifest.</p>
 *
 * @since 2026-10-21
 */
public final class DocumentIdCalculator {
    /** The clinic secret, base64, at least 32 bytes once decoded. */
    public static final String SECRET_PROPERTY = "pdf.signing.document_id.secret";
    static final int MIN_SECRET_BYTES = 32;
    private static final byte[] LABEL = "carlos-emr/document-id/v1".getBytes(StandardCharsets.US_ASCII);
    private static final String HMAC = "HmacSHA256";

    private final byte[] secret;

    /**
     * @param secret the decoded clinic secret, at least {@value #MIN_SECRET_BYTES} bytes
     */
    public DocumentIdCalculator(byte[] secret) {
        if (secret == null || secret.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("The document ID secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.secret = Arrays.copyOf(secret, secret.length);
    }

    /**
     * The calculator for the configured secret, or empty when {@value #SECRET_PROPERTY} is not set.
     *
     * @throws IllegalStateException when the setting is present but is not base64 of at least
     *         {@value #MIN_SECRET_BYTES} bytes, so a broken setting stops a send instead of silently
     *         dropping the manifest
     */
    public static Optional<DocumentIdCalculator> fromCarlosProperties() {
        // Commented out by default; reading it this way avoids CarlosProperties' missing-key warning.
        Object value = CarlosProperties.getInstance().get(SECRET_PROPERTY);
        String text = value == null ? "" : value.toString().trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException standardFailed) {
            try {
                decoded = Base64.getUrlDecoder().decode(text);
            } catch (IllegalArgumentException urlFailed) {
                throw new IllegalStateException(SECRET_PROPERTY + " is not base64", urlFailed);
            }
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(SECRET_PROPERTY + " must decode to at least " + MIN_SECRET_BYTES + " bytes");
        }
        return Optional.of(new DocumentIdCalculator(decoded));
    }

    /**
     * @param source the kind of record, such as {@code eform} or {@code document}; it may not
     *        contain {@code :}, so the first {@code :} always ends it and no two pairs give the
     *        same input
     * @param internalId the record's ID within that kind
     * @return the document ID, base64url without padding (43 characters)
     */
    public String documentId(String source, String internalId) {
        if (source == null || source.isBlank() || internalId == null || internalId.isBlank()) {
            throw new IllegalArgumentException("A document ID needs a source and an internal ID");
        }
        if (source.indexOf(':') >= 0) {
            throw new IllegalArgumentException("A document ID source may not contain ':'");
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            mac.update(LABEL);
            mac.update((byte) 0);
            mac.update((source + ":" + internalId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }
}
