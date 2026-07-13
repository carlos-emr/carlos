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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao.projection;

/**
 * One row from
 * {@code DiagnosticCodeDao.findDiagnosictsAndCtlDiagCodesByServiceType} —
 * a {@code DiagnosticCode} joined with its matching {@code CtlDiagCode}
 * row, projected to just the diagnostic-code identifier and its
 * description (the only fields the billing-form layer 2 search panel
 * actually reads).
 *
 * @since 2026-05-01
 */
public record DiagnosticCodeRow(String diagnosticCode, String description) {
    public DiagnosticCodeRow {
        diagnosticCode = diagnosticCode == null ? "" : diagnosticCode;
        description = description == null ? "" : description;
    }
}
