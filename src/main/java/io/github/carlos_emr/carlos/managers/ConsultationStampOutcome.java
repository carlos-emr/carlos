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

import io.github.carlos_emr.carlos.commn.model.DigitalSignature;

/**
 * Result of an attempt to apply a provider stamp to a consultation. Replaces a bare {@code null}
 * return so the caller can distinguish a <em>benign</em> non-application (signatures disabled, no
 * session) from a <em>genuine failure</em> (not permitted, stamp file unreadable, persistence error)
 * and decide whether to warn the provider that the consultation saved without its signature.
 *
 * @param status   the outcome category
 * @param signature the persisted signature when {@link Status#SAVED}, otherwise {@code null}
 */
public record ConsultationStampOutcome(Status status, DigitalSignature signature) {

    /**
     * Enforces the invariant that a signature is present iff the stamp was saved, so an inconsistent
     * outcome (e.g. {@code SAVED} with no signature, or a failure carrying one) cannot be built.
     */
    public ConsultationStampOutcome {
        if ((status == Status.SAVED) != (signature != null)) {
            throw new IllegalArgumentException("signature must be present iff status is SAVED");
        }
    }

    /**
     * Outcome categories. {@link #SIGNATURES_DISABLED} and {@link #NO_SESSION} are expected,
     * benign states (logged at debug); the remainder are genuine failures the operator/provider
     * should hear about (logged at error). See {@link #isGenuineFailure()}.
     */
    public enum Status {
        SAVED,
        SIGNATURES_DISABLED,
        NO_SESSION,
        NOT_PERMITTED,
        STAMP_FILE_MISSING,
        ERROR
    }

    static ConsultationStampOutcome saved(DigitalSignature signature) {
        return new ConsultationStampOutcome(Status.SAVED, signature);
    }

    static ConsultationStampOutcome of(Status status) {
        return new ConsultationStampOutcome(status, null);
    }

    public boolean isSaved() {
        return status == Status.SAVED;
    }

    /**
     * @return {@code true} when the stamp could not be applied for a reason the provider should be
     *         warned about (not permitted, stamp file missing/unreadable, or a persistence error) —
     *         as opposed to the benign disabled/no-session states
     */
    public boolean isGenuineFailure() {
        return status == Status.NOT_PERMITTED
                || status == Status.STAMP_FILE_MISSING
                || status == Status.ERROR;
    }
}
