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
package io.github.carlos_emr.carlos.sms;

import java.util.Arrays;
import java.util.Optional;

/**
 * Why a user opened the full text of an SMS. The chosen value is written to the body-read audit
 * record, so only these reasons are accepted; free text would let the audit trail say anything.
 *
 * @since 2026-09-24
 */
public enum SmsMessageBodyReadReason {
    /** Reviewing what the patient was told or replied, as part of their care. */
    CARE_REVIEW,
    /** Checking a delivery problem, such as a failed or blocked message. */
    DELIVERY_REVIEW,
    /** The patient asked what was sent to them. */
    PATIENT_REQUEST;

    /**
     * @param value a request parameter
     * @return the matching reason, or empty for anything not on the list (including null)
     */
    public static Optional<SmsMessageBodyReadReason> fromParameter(String value) {
        return Arrays.stream(values()).filter(reason -> reason.name().equals(value)).findFirst();
    }
}
