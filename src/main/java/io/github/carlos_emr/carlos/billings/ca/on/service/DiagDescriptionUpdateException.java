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
package io.github.carlos_emr.carlos.billings.ca.on.service;

/**
 * Thrown when a diagnostic-code description update cannot complete. Distinguishes
 * missing/malformed codes and persistence failures from a successful update so
 * callers do not collapse all outcomes into a boolean.
 */
public class DiagDescriptionUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String diagnosticCode;
    private final String reason;

    public DiagDescriptionUpdateException(String diagnosticCode, Throwable cause) {
        this(diagnosticCode, "persistence failure", cause);
    }

    public DiagDescriptionUpdateException(String diagnosticCode, String reason) {
        this(diagnosticCode, reason, null);
    }

    public DiagDescriptionUpdateException(String diagnosticCode, String reason, Throwable cause) {
        super("Diagnostic code description update failed for "
                + (diagnosticCode == null || diagnosticCode.isBlank() ? "<missing>" : diagnosticCode)
                + ": " + reason, cause);
        this.diagnosticCode = diagnosticCode;
        this.reason = reason;
    }

    public String diagnosticCode() {
        return diagnosticCode;
    }

    public String reason() {
        return reason;
    }
}
