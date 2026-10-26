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

/**
 * The message lists the SMS queue page shows for each SMS provider. Each is paged on its own
 * ({@link SmsQueuePages}).
 *
 * @since 2026-10-16
 */
public enum SmsQueueList {
    /** Queued messages that are overdue. Not tied to the time period. */
    OVERDUE("overdue", false),
    /** Sends with an unknown outcome. Not tied to the time period. */
    STALE("stale", false),
    /** Failed messages within the time period. */
    FAILED("failed", true),
    /** Messages blocked by consent within the time period. */
    BLOCKED("blocked", true);

    private final String parameterValue;
    private final boolean inWindow;

    SmsQueueList(String parameterValue, boolean inWindow) {
        this.parameterValue = parameterValue;
        this.inWindow = inWindow;
    }

    /** @return how this list is named in its page parameter, such as {@code failed} */
    public String parameterValue() {
        return parameterValue;
    }

    /**
     * @return whether the list covers only the chosen {@link SmsQueueWindow}, so that choosing another
     *         period changes what is on its pages
     */
    public boolean inWindow() {
        return inWindow;
    }
}
