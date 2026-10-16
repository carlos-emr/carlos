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
package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;

import java.time.Instant;

/**
 * The operational columns of one outbound {@code sms_transaction} row, read by a projection query for the
 * Administration &gt; SMS queue view.
 * <p>
 * The projection deliberately leaves out the message body, the sender number, operator/provider messages and
 * provider metadata, so none of them is ever loaded for this view. The recipient number is still the full
 * stored value: {@code SmsQueueViewModelAssembler} masks it to the last four digits before anything reaches
 * the page, and {@link #toString()} is redacted so the number cannot leak through diagnostics.
 *
 * @param id                the {@code sms_transaction} id
 * @param providerType      the SMS provider
 * @param status            the row's status
 * @param demographicNo     the patient's demographic number, or {@code null} for system tests
 * @param toPhoneNumber     the full stored recipient number; mask before display
 * @param attemptCount      send attempts so far
 * @param errorCode         the last recorded error code, or {@code null}
 * @param consentReasonCode the consent reason code of a blocked row, or {@code null}
 * @param createdAt         when the row was created
 * @param updatedAt         when the row last changed
 * @param nextAttemptAt     when a queued row is next due, or {@code null} (due since creation)
 * @param lastAttemptAt     when the last send attempt started, or {@code null}
 * @since 2026-09-28
 */
public record SmsQueueRowDto(
        Long id,
        SmsProviderType providerType,
        SmsStatus status,
        Integer demographicNo,
        String toPhoneNumber,
        int attemptCount,
        String errorCode,
        String consentReasonCode,
        Instant createdAt,
        Instant updatedAt,
        Instant nextAttemptAt,
        Instant lastAttemptAt
) {
    @Override
    public String toString() {
        return "SmsQueueRowDto[redacted]";
    }
}
