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
package io.github.carlos_emr.carlos.demographic.cdsimport.command;

/** A review is a versioned correction/decline request, never an acknowledgement that clears a blocker. */
public record CdsImportReviewCommand(String kind, Long diagnosticId, String patientKey,
        String unitKey, long expectedVersion, String sourceXPath, String replacementValue,
        String reason) {
    public CdsImportReviewCommand {
        if (!("CORRECT_VALUE".equals(kind) || "DECLINE_PATIENT".equals(kind) || "APPROVE_UNWRITTEN_REVISION".equals(kind))
                || diagnosticId == null || diagnosticId < 1 || expectedVersion < 0
                || reason == null || reason.isBlank() || reason.length() > 4000
                || patientKey == null || patientKey.length() > 64
                || (unitKey != null && unitKey.length() > 128)
                || (sourceXPath != null && sourceXPath.length() > 2048)
                || (replacementValue != null && replacementValue.length() > 65536)) {
            throw new IllegalArgumentException("IMPORT_REVIEW_INVALID");
        }
    }

    @Override public String toString() { return "CdsImportReviewCommand[protected]"; }
}
