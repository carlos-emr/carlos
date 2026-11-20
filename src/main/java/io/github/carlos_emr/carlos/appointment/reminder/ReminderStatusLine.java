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
package io.github.carlos_emr.carlos.appointment.reminder;

import java.util.Objects;

/**
 * The line under the Email Reminder box on Edit Appointment (#5887): a bundle key, and the one value it takes
 * (a formatted date and time, or empty). Every key is a MessageFormat pattern, so the page always passes the
 * value.
 *
 * @since 2026-11-12
 */
public record ReminderStatusLine(String messageKey, String argument) {

    public ReminderStatusLine {
        Objects.requireNonNull(messageKey, "messageKey");
        argument = argument == null ? "" : argument;
    }
}
