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
package io.github.carlos_emr.carlos.sms.viewmodel;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * The time periods the SMS queue page offers for its "Failed" and "Blocked by consent" sections. A message
 * is inside a period when it last changed ({@code updatedAt}) within it.
 * <p>
 * The page sends the choice as the {@code window} request parameter. Only the values listed here are
 * accepted; anything else falls back to {@link #DEFAULT}. The page is given the chosen value's own
 * {@link #parameterValue()}, never the text the browser sent.
 *
 * @since 2026-09-29
 */
public enum SmsQueueWindow {
    LAST_7_DAYS("7d", Duration.ofDays(7)),
    LAST_30_DAYS("30d", Duration.ofDays(30)),
    LAST_90_DAYS("90d", Duration.ofDays(90)),
    /** No limit: every message, however old. */
    ALL_TIME("all", null);

    /** Used when the page is opened without a choice, or with one that is not allowed. */
    public static final SmsQueueWindow DEFAULT = LAST_30_DAYS;

    private final String parameterValue;
    private final Duration length;

    SmsQueueWindow(String parameterValue, Duration length) {
        this.parameterValue = parameterValue;
        this.length = length;
    }

    /**
     * @param parameter the {@code window} request parameter as sent; may be {@code null}
     * @return the period with exactly that parameter value (lower case, no surrounding spaces), or
     *         {@link #DEFAULT} for {@code null}, blank or anything else
     */
    public static SmsQueueWindow fromParameter(String parameter) {
        for (SmsQueueWindow window : values()) {
            if (window.parameterValue.equals(parameter)) {
                return window;
            }
        }
        return DEFAULT;
    }

    /** @return every period's parameter value, in the order the page lists them */
    public static List<String> parameterValues() {
        return Arrays.stream(values()).map(SmsQueueWindow::parameterValue).toList();
    }

    /** @return the value of the {@code window} request parameter that selects this period */
    public String parameterValue() {
        return parameterValue;
    }

    /**
     * @param now the current time
     * @return the start of this period, or {@code null} for {@link #ALL_TIME}
     */
    public Instant since(Instant now) {
        return length == null ? null : now.minus(length);
    }
}
