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
package io.github.carlos_emr.carlos.integration.patientportal;

import java.util.Set;

/** The fixed-vocabulary request from #3849; callers keep operationId unchanged across retries. */
public record PatientPortalBookingPromptRequest(
        String operationId, String urgency, String appointmentType, String suggestedBy) {
    static final Set<String> URGENCIES = Set.of("routine", "soon", "as_soon_as_possible");
    static final Set<String> APPOINTMENT_TYPES = Set.of("follow_up", "annual_exam", "lab_review");

    public PatientPortalBookingPromptRequest {
        if (operationId == null || !operationId.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new PortalRequestPreparationException("booking operation id is invalid");
        }
        if (urgency == null || !URGENCIES.contains(urgency)
                || appointmentType == null || !APPOINTMENT_TYPES.contains(appointmentType)) {
            throw new PortalRequestPreparationException("booking vocabulary is invalid");
        }
        if (suggestedBy != null) {
            suggestedBy = suggestedBy.strip();
            if (suggestedBy.isEmpty()
                    || suggestedBy.codePointCount(0, suggestedBy.length()) > 128
                    || suggestedBy.codePoints().anyMatch(PatientPortalBookingPromptRequest::hidden)) {
                throw new PortalRequestPreparationException("suggesting provider name is invalid");
            }
        }
    }

    private static boolean hidden(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.FORMAT
                && codePoint != 0x200C && codePoint != 0x200D && codePoint != 0x00AD;
    }

    /** Provider names and correlation identifiers must not enter incidental logs. */
    @Override
    public String toString() {
        return "PatientPortalBookingPromptRequest[details=REDACTED]";
    }
}
