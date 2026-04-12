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
 * <p>Prevents log injection attacks (CRLF injection / log forging) by escaping control
 * characters in user-supplied values before they appear in log output. All user-supplied
 * values that appear in log statements should be passed through {@link #sanitize(String)}
 * before being included.</p>
 *
 * <p><strong>Important:</strong> Sanitizing a value does NOT make it appropriate to log.
 * Patient Health Information (PHI) — including patient names, health insurance numbers (HIN),
 * dates of birth, and clinical data — must NEVER appear in log output regardless of
 * sanitization. This utility prevents log injection attacks on values that are
 * <em>already appropriate</em> to log (IDs, form names, file paths, operation types).</p>
 *
 * <p>Internally truncates the raw input to {@value #DEFAULT_MAX_LENGTH} characters, then
 * uses the OWASP Java Encoder ({@code Encode.forJava()}) to escape control characters
 * (including CR/LF), quotes, backslashes, and non-ASCII characters (accented characters
 * common in French-Canadian names will appear as octal/Unicode escape sequences in logs).
 * A post-encoding safety bound of {@value #MAX_ENCODED_LENGTH} characters caps the final
 * output to prevent log flooding from adversarial inputs that expand during encoding.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * // Before (unsafe — user input concatenated directly):
 * logger.error("Invalid document ID: " + docId);
 *
 * // After (safe — user input sanitized and parameterized):
 * logger.error("Invalid document ID: {}", LogSanitizer.sanitize(docId));
 *
 * // For longer values where truncation at 200 would lose diagnostics:
 * logger.warn("SQL: {}", LogSanitizer.sanitize(sqlStatement, 1000));
 * </pre>
 *
 * <p><strong>Note:</strong> SLF4J/Log4j2 parameterized logging ({@code {}}) alone does NOT
 * prevent log injection — CRLF characters in parameter values are written to logs verbatim.
 * {@code LogSanitizer.sanitize()} is required to neutralize control characters.</p>
 *
 * @since 2026-04-02
 */
public final class LogSanitizer {

    /** Default maximum number of characters for raw input before encoding. */
    static final int DEFAULT_MAX_LENGTH = 200;

    /**
     * Post-encoding expansion factor. {@code Encode.forJava()} expands control characters
     * (e.g. {@code \n} → {@code \\n}), with a worst-case expansion of 6x for non-ASCII
     * characters encoded as Unicode escape sequences (one character → six output characters).
     */
    private static final int ENCODING_EXPANSION_FACTOR = 6;

    /**
     * Post-encoding output bound for the default max length. When using the custom-length
     * overload, the bound is {@code maxLength * ENCODING_EXPANSION_FACTOR} instead.
     */
    static final int MAX_ENCODED_LENGTH = DEFAULT_MAX_LENGTH * ENCODING_EXPANSION_FACTOR;

    private LogSanitizer() {
        // utility class — no instances
    }

    /**
     * Sanitizes a {@code String} value for safe inclusion in a log statement.
     *
     * <p>Truncates the raw input to {@value #DEFAULT_MAX_LENGTH} characters, then applies
     * OWASP Java Encoder escaping (converts CR, LF, other control characters, quotes,
     * backslashes, and non-ASCII characters to their Java escape sequences). Appends
     * {@code "..."} when truncation occurs. A post-encoding safety bound prevents
     * adversarial inputs from producing excessively long encoded output.</p>
     *
     * @param input String the value to sanitize; may be {@code null}
     * @return String the sanitized string, or the literal {@code "null"} if input is null; never returns {@code null}
     */
    public static String sanitize(String input) {
        return sanitize(input, DEFAULT_MAX_LENGTH);
    }

    /**
     * Sanitizes a {@code String} value with a custom maximum length.
     *
     * <p>Use this overload when the default {@value #DEFAULT_MAX_LENGTH}-character limit
     * would lose critical diagnostic information (e.g. SQL statements, file paths).
     * A post-encoding safety bound of {@code maxLength * 6} characters caps the output
     * to prevent log flooding when control-character-heavy inputs expand during encoding.
     * If {@code maxLength} is less than 1, defaults to {@value #DEFAULT_MAX_LENGTH}.</p>
     *
     * @param input String the value to sanitize; may be {@code null}
     * @param maxLength int the maximum number of raw characters to keep before encoding;
     *                  values less than 1 are treated as {@value #DEFAULT_MAX_LENGTH}
     * @return String the sanitized string; never {@code null}
     */
    public static String sanitize(String input, int maxLength) {
        if (input == null) {
            return "null";
        }
        if (maxLength < 1) {
            maxLength = DEFAULT_MAX_LENGTH;
        }
        boolean truncated = input.length() > maxLength;
        if (truncated) {
            input = input.substring(0, maxLength);
        }
        String encoded = Encode.forJava(input);
        int encodedLimit = maxLength * ENCODING_EXPANSION_FACTOR;
        if (encoded.length() > encodedLimit) {
            encoded = encoded.substring(0, encodedLimit);
            truncated = true;
        }
        return truncated ? encoded + "..." : encoded;
    }

    /**
     * Sanitizes an arbitrary {@code Object} value for safe inclusion in a log statement.
     *
     * <p>Converts the object to its {@link Object#toString()} representation and delegates
     * to {@link #sanitize(String)}. If {@code toString()} throws an exception, returns
     * a safe fallback string containing the class name and exception type. Also catches
     * {@code StackOverflowError} specifically (for recursive {@code toString()} implementations),
     * but lets other {@code Error} subclasses propagate.</p>
     *
     * <p>Named {@code sanitizeObject} (not {@code sanitize}) to avoid a confusing overload
     * with {@link #sanitize(String)} — callers must opt in explicitly, and dispatch does not
     * depend on the static type of the argument.</p>
     *
     * @param input Object the value to sanitize; may be {@code null}
     * @return String the sanitized string; never {@code null}
     */
    public static String sanitizeObject(Object input) {
        if (input == null) {
            return "null";
        }
        try {
            return sanitize(input.toString());
        } catch (StackOverflowError e) {
            return "[toString() failed: " + input.getClass().getName() + " (StackOverflowError)]";
        } catch (Exception e) {
            return "[toString() failed: " + input.getClass().getName() + " (" + e.getClass().getSimpleName() + ")]";
        }
    }
}
