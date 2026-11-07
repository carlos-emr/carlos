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
package io.github.carlos_emr.carlos.demographic.cdsexport.support;

/** Finite public review reasons; protected clinical detail belongs in encrypted diagnostics, never exception text. */
public final class CdsExportReviewException extends RuntimeException {
    public enum Reason {
        UNMAPPED_DATA, PREVENTION_PROGRAM_SCOPE, MERGED_PATIENT_SCOPE, SOURCE_CHANGED, AMBIGUOUS_SOURCE, INCOMPLETE_RENDER, MISSING_ATTACHMENT,
        AUDIT_ACCESS_UNAVAILABLE, RECIPIENT_UNAVAILABLE, RESOURCE_LIMIT, CLINICAL_VALIDATION_FAILED;
        public String code() { return "CDS_EXPORT_" + name(); }
    }
    private final Reason reason;
    public CdsExportReviewException(Reason reason) { super(java.util.Objects.requireNonNull(reason).code()); this.reason = reason; }
    public Reason reason() { return reason; }
}
