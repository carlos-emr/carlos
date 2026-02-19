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
package io.github.carlos_emr.carlos.drools;

/**
 * Checked exception thrown when Drools DRL rule compilation or loading fails.
 *
 * <p>This exception is used throughout the CARLOS EMR Drools subsystem to signal
 * errors during the compilation of DRL (Drools Rule Language) rules into executable
 * knowledge bases, or when DRL content cannot be read from its source. Common causes
 * include syntax errors in DRL files, invalid import statements, missing fact class
 * definitions, or I/O failures when reading DRL resources.</p>
 *
 * <p>Introduced as part of the Drools 2.0 to 7.74.1 KIE API migration to provide
 * a consistent, checked exception type for all Drools compilation and loading errors.</p>
 *
 * <p><strong>Usage example:</strong></p>
 * <pre>{@code
 * try {
 *     KieBase kieBase = DroolsHelper.createKieBaseFromDrl(drlContent);
 * } catch (DroolsCompilationException e) {
 *     log.error("Failed to compile clinical decision support rules", e);
 * }
 * }</pre>
 *
 * @since 2026-02-17
 * @see DroolsHelper#createKieBaseFromDrl(String)
 * @see DroolsHelper#loadFromInputStream(java.io.InputStream)
 * @see DroolsHelper#loadFromUrl(java.net.URL)
 */
public class DroolsCompilationException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new compilation exception with the specified detail message.
     *
     * @param message String describing the compilation failure, typically including
     *                the DRL source identifier and error details
     */
    public DroolsCompilationException(String message) {
        super(message);
    }

    /**
     * Constructs a new compilation exception with the specified detail message and cause.
     *
     * @param message String describing the compilation failure context
     * @param cause Throwable the underlying cause (e.g., KIE builder errors, I/O failures)
     */
    public DroolsCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
