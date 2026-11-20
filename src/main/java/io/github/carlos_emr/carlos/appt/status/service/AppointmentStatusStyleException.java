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
package io.github.carlos_emr.carlos.appt.status.service;

/**
 * A posted appointment status value that the status rules refuse: a blank or over-long
 * description, a colour that is not {@code #rrggbb}, an icon outside
 * {@link AppointmentStatusMgr#ICON_SET}, or an enable/disable value other than 0 or 1.
 *
 * <p>The exception carries the {@code oscarResources} key of the message the settings page shows,
 * so the rules live in one place ({@code AppointmentStatusMgrImpl}) and the action only translates
 * the key. The message itself is the key too; it never quotes the posted value, which keeps
 * request text out of the logs.</p>
 *
 * @since 2026-11-04
 */
public class AppointmentStatusStyleException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String messageKey;

    /**
     * @param messageKey String the resource key of the message to show
     */
    public AppointmentStatusStyleException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    /**
     * @return String the resource key of the message the settings page shows
     */
    public String getMessageKey() {
        return messageKey;
    }
}
