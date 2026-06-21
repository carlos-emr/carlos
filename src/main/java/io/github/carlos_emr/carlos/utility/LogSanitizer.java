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

/**
 * Transitional shim — delegates to {@link LogSafe}.
 *
 * <p>Provided as an incremental migration adapter so existing call sites can be
 * moved off ad-hoc sanitization and onto {@link LogSafe} without a single large
 * change. New code should call {@link LogSafe} directly.</p>
 *
 * @deprecated Use {@link LogSafe} directly.
 */
@Deprecated(forRemoval = true)
public final class LogSanitizer {

    private LogSanitizer() {
        // utility class — no instances
    }

    public static String sanitize(String input) {
        return LogSafe.sanitize(input);
    }

    public static String sanitize(String input, int maxLength) {
        return LogSafe.sanitize(input, maxLength);
    }
}
