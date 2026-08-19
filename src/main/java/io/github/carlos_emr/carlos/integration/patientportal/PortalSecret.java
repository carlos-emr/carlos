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
package io.github.carlos_emr.carlos.integration.patientportal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * A credential that must not be rendered, serialized, or logged by accident.
 *
 * <p>Three values in this package are credentials: the portal service token, the one-time patient
 * invite token, and the passphrase for an encrypted message. Holding them as {@code String} left
 * three separate protections to remember — and remembering is not a control. This type makes the
 * protections structural:
 *
 * <ul>
 *   <li><b>Rendering is safe by default.</b> {@link #toString()} never emits the value, so a secret
 *       cannot reach a log line through string interpolation, a collection dump, or a debugger view.
 *   <li><b>Jackson cannot serialize it.</b> There is no getter and no public field, so a DTO holding
 *       one fails to serialize rather than quietly publishing the credential the day someone returns
 *       it from a REST service or puts it on a Struts value stack. A loud failure is the correct
 *       outcome there.
 *   <li><b>Reading it is greppable.</b> {@link #expose()} is the single audit surface. {@code
 *       secret()} and {@code inviteToken()} looked like every other accessor; {@code expose()} does
 *       not, and it gives a static-analysis rule one anchor to match on.
 * </ul>
 *
 * <p>Equality is constant-time so a comparison cannot leak the value through timing, and {@link
 * #hashCode()} is deliberately constant — these are not sensible map keys, and a hash of a
 * credential is itself a disclosure of sorts. The trade is that a {@code HashSet} of secrets
 * degrades to linear scanning, which nothing here does.
 *
 * @since 2026-08-19
 */
public final class PortalSecret {

    private static final String DESCRIPTION = "PortalSecret[REDACTED]";

    private final String value;

    private PortalSecret(String value) {
        this.value = value;
    }

    /**
     * Wraps a credential.
     *
     * @param value the raw credential; must not be blank
     * @throws IllegalArgumentException if the value is null or blank
     */
    public static PortalSecret of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("portal secret must not be blank");
        }
        return new PortalSecret(value);
    }

    /**
     * Wraps a credential that the portal may legitimately omit.
     *
     * @return the wrapped value, or {@code null} when {@code value} is null or blank
     */
    static PortalSecret ofNullable(String value) {
        return value == null || value.isBlank() ? null : new PortalSecret(value);
    }

    /**
     * Returns the raw credential.
     *
     * <p>Every call site is a place a credential leaves containment. Use it only where the value is
     * genuinely required — an outbound header, or the message being sent to the patient — and never
     * to build a log line or an exception message.
     */
    public String expose() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PortalSecret secret)) {
            return false;
        }
        return MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8),
                secret.value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Constant by design; see the class notes on why a credential's hash is not published.
     */
    @Override
    public int hashCode() {
        return Objects.hash(PortalSecret.class);
    }

    @Override
    public String toString() {
        return DESCRIPTION;
    }
}
