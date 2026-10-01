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

import java.util.Map;

/**
 * SMS settings as submitted from Administration &gt; SMS. Secret fields are write-only: a blank
 * {@code webhookSecret} or credential value means "keep the stored one", and
 * {@code clearWebhookSecret} removes the stored secret.
 *
 * @param providerType       the provider to send through
 * @param enabled            whether CARLOS may send SMS at all
 * @param schedulerEnabled   whether the queue scheduler runs
 * @param senderNumber       the number messages are sent from, any common format; blank for none
 * @param webhookSecret      a new webhook secret, or blank to keep the stored one
 * @param clearWebhookSecret remove the stored webhook secret
 * @param credentials        new credential values by field name; blank values keep the stored ones
 * @since 2026-09-24
 */
public record SmsConfigUpdateDto(SmsProviderType providerType, boolean enabled, boolean schedulerEnabled,
                                 String senderNumber, String webhookSecret, boolean clearWebhookSecret,
                                 Map<String, String> credentials) {

    public SmsConfigUpdateDto {
        credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
    }

    /** Redacted: the record holds secrets. */
    @Override
    public String toString() {
        return "SmsConfigUpdateDto[redacted]";
    }
}
