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
package io.github.carlos_emr.carlos.sms.service;

import java.time.Duration;
import java.util.Objects;

/**
 * How many texts an SMS provider may be sent within a fixed window, counted across every CARLOS server that
 * shares the database. Each provider states its own ({@link SmsProviderClient#sendRateLimit()}); a text that
 * would go over waits in the queue.
 *
 * @param maxSends the most texts per window, at least 1
 * @param window   the window's length, at least one millisecond
 * @since 2026-10-08
 */
public record SmsSendRateLimit(int maxSends, Duration window) {
    /** The limit for a provider that states none: 5 texts every 5 seconds. */
    public static final SmsSendRateLimit DEFAULT = new SmsSendRateLimit(5, Duration.ofSeconds(5));

    public SmsSendRateLimit {
        Objects.requireNonNull(window, "SMS send rate window is required");
        if (maxSends < 1) {
            throw new IllegalArgumentException("SMS send rate must allow at least one text per window");
        }
        if (window.toMillis() < 1) {
            throw new IllegalArgumentException("SMS send rate window must be at least one millisecond");
        }
    }
}
