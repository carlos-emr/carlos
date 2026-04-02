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

package io.github.carlos_emr.carlos.utility;

import org.owasp.encoder.Encode;

/**
 * Utility class for sanitizing user-controlled data before including it in log statements.
 *
 * <p>Prevents log injection attacks (CRLF injection / log forging) and mitigates the risk
 * of PHI (Protected Health Information) leaking into application log files in violation of
 * PIPEDA/HIPAA requirements. All user-supplied values that appear in log statements should
 * be passed through {@link #sanitize(String)} before being included.</p>
 *
 * <p>Internally uses the OWASP Java Encoder ({@code Encode.forJava()}) to escape control
 * characters (including CR/LF), then truncates the result to {@value #MAX_LOG_LENGTH}
 * characters so that very long inputs cannot flood the log.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * // Before (unsafe — user input concatenated directly):
 * logger.error("Invalid document ID: " + docId);
 *
 * // After (safe — user input sanitized and parameterized):
 * logger.error("Invalid document ID: {}", LogSanitizer.sanitize(docId));
 * </pre>
 *
 * @since 2026-04-02
 */
public final class LogSanitizer {

    /** Maximum number of characters allowed in a sanitized log value. */
    static final int MAX_LOG_LENGTH = 200;

    private LogSanitizer() {
        // utility class — no instances
    }

    /**
     * Sanitizes a {@code String} value for safe inclusion in a log statement.
     *
     * <p>Applies OWASP Java Encoder escaping (strips/encodes CR, LF, and other control
     * characters) and then truncates the result to {@value #MAX_LOG_LENGTH} characters,
     * appending {@code "..."} when truncation occurs.</p>
     *
     * @param input String the value to sanitize; may be {@code null}
     * @return String the sanitized, truncated string; never {@code null}
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "null";
        }
        String encoded = Encode.forJava(input);
        if (encoded.length() > MAX_LOG_LENGTH) {
            return encoded.substring(0, MAX_LOG_LENGTH) + "...";
        }
        return encoded;
    }

    /**
     * Sanitizes an arbitrary {@code Object} value for safe inclusion in a log statement.
     *
     * <p>Converts the object to its {@link Object#toString()} representation and delegates
     * to {@link #sanitize(String)}.</p>
     *
     * @param input Object the value to sanitize; may be {@code null}
     * @return String the sanitized, truncated string; never {@code null}
     */
    public static String sanitize(Object input) {
        if (input == null) {
            return "null";
        }
        return sanitize(input.toString());
    }
}
