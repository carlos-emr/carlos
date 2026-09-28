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

/**
 * One grouped count of outbound {@code sms_transaction} rows, for the Administration &gt; SMS queue view.
 * The grouping value is always a machine code (a status name, an error code or a consent reason code),
 * never patient data.
 *
 * @param providerType the SMS provider the rows belong to
 * @param code         the value the rows share, or {@code null} when they have none (for example a failure
 *                     recorded without an error code)
 * @param count        how many rows share it
 * @since 2026-09-28
 */
public record SmsQueueCountDto(SmsProviderType providerType, String code, long count) {
}
