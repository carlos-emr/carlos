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

package io.github.carlos_emr.carlos.email.archive;

import java.io.IOException;

/**
 * An archived artifact could not be sealed into, or opened from, its encrypted envelope.
 *
 * <p>An {@link IOException} so the archive service keeps its existing contract: a caller of
 * {@code readArchivedArtifact} gets an {@code IOException} and no bytes. The {@link Reason} lets
 * that service audit a missing key differently from tampering without parsing messages.</p>
 *
 * <p>Messages are fixed text plus, at most, a numeric key id. They never carry key material,
 * plaintext, ciphertext or anything from the archived message.</p>
 *
 * @since 2026-09-30
 */
public class OutboundEmailArchiveEnvelopeException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Why the envelope could not be used. */
    public enum Reason {
        /** Too short, wrong marker, or a field outside its allowed range. */
        MALFORMED,
        /** A format version or algorithm this build does not know. Fails closed, never guessed. */
        UNSUPPORTED,
        /** The key id in the header is not in the configured keyring. */
        KEY_UNAVAILABLE,
        /**
         * The AES-GCM tag did not verify: the ciphertext, header or bound archive context was
         * changed, or the key under that id is not the key that sealed it.
         */
        AUTHENTICATION_FAILED,
        /**
         * Decryption failed inside the JCE provider for a reason other than the tag: a provider or
         * configuration fault, not evidence about the artifact or the key.
         */
        DECRYPTION_ERROR,
        /** Sealing failed inside the JCE provider. */
        ENCRYPTION_FAILED
    }

    private final Reason reason;

    public OutboundEmailArchiveEnvelopeException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public OutboundEmailArchiveEnvelopeException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** @return why the envelope could not be used */
    public Reason getReason() {
        return reason;
    }
}
