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
package io.github.carlos_emr.carlos.managers;

import org.apache.commons.lang3.StringUtils;

/**
 * Result of persisting a freshly captured manual consultation signature before PDF preview rendering.
 *
 * @param status      the outcome category
 * @param signatureId the persisted {@code DigitalSignature.id} when {@link Status#SAVED}, otherwise empty
 */
public record ConsultationPreviewSignatureOutcome(Status status, String signatureId) {

    public ConsultationPreviewSignatureOutcome {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        signatureId = StringUtils.trimToEmpty(signatureId);
        if ((status == Status.SAVED) != StringUtils.isNotBlank(signatureId)) {
            throw new IllegalArgumentException("signatureId must be present iff status is SAVED");
        }
    }

    public enum Status {
        SAVED,
        NOT_CAPTURED,
        PERSIST_FAILED,
        REQUEST_NOT_FOUND,
        DEMOGRAPHIC_MISMATCH
    }

    static ConsultationPreviewSignatureOutcome saved(String signatureId) {
        return new ConsultationPreviewSignatureOutcome(Status.SAVED, signatureId);
    }

    static ConsultationPreviewSignatureOutcome of(Status status) {
        return new ConsultationPreviewSignatureOutcome(status, "");
    }

    public boolean isSaved() {
        return status == Status.SAVED;
    }
}
