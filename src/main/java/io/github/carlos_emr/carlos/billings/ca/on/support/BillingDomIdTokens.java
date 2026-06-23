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
package io.github.carlos_emr.carlos.billings.ca.on.support;

import java.util.regex.Pattern;

/**
 * String sanitiser that turns a {@code ctl_billservice} service-type code
 * into a DOM-id-safe token. Replaces every character outside
 * {@code [A-Za-z0-9_-]} with {@code _}.
 *
 * <p>Service-type codes are conventionally short alphanumerics, so this is
 * a no-op in practice — it exists to keep the rendered DOM well-formed if
 * a malformed row ever makes it into the table.</p>
 *
 * <p>Distinct malformed inputs can collide (e.g. {@code "12.3"} and
 * {@code "12_3"} both yield {@code "12_3"}). This is a deliberate trade-off:
 * the token only groups display rows and show/hide controls — it carries no
 * authorization or data-selection weight — and the one-for-one replacement
 * keeps the writer/reader round-trip trivially predictable.</p>
 *
 * @since 2026-04-27
 */
public final class BillingDomIdTokens {
    private static final Pattern UNSAFE_ID_CHARS = Pattern.compile("[^A-Za-z0-9_-]");

    private BillingDomIdTokens() {}

    /** Returns a DOM-id-safe rendering of {@code s} (or empty if {@code s} is null). */
    public static String sanitize(String s) {
        if (s == null) return "";
        return UNSAFE_ID_CHARS.matcher(s).replaceAll("_");
    }
}
