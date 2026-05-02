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
 * Thrown when a diagnostic-code description update cannot complete because
 * the persistence layer failed. A missing code is a validation outcome and is
 * still reported as "not updated"; DAO, lock, and merge failures use this
 * exception so Spring rolls back the transaction instead of showing a generic
 * form failure.
 */
public class DiagDescriptionUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String diagnosticCode;

    public DiagDescriptionUpdateException(String diagnosticCode, Throwable cause) {
        super("Diagnostic code description update failed for " + diagnosticCode, cause);
        this.diagnosticCode = diagnosticCode;
    }

    public String diagnosticCode() {
        return diagnosticCode;
    }
}
