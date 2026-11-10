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
package io.github.carlos_emr.carlos.appt.location;

/**
 * A Location List change refused for a reason the administrator can act on, such as a name
 * another active location already has. Nothing was saved.
 *
 * <p>Carries the {@code oscarResources} key of the message the settings page shows and that
 * message's single parameter, if it takes one. The exception message is the key, never posted
 * text, so a refused change logs nothing a user typed.</p>
 *
 * @since 2026-11-04
 */
public class AppointmentLocationChangeRefusedException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String messageKey;
    private final String messageParam;

    /**
     * @param messageKey String the resource key of the message to show
     * @param messageParam String the message's parameter, or null if it takes none
     */
    public AppointmentLocationChangeRefusedException(String messageKey, String messageParam) {
        super(messageKey);
        this.messageKey = messageKey;
        this.messageParam = messageParam;
    }

    /**
     * @return String the resource key of the message the settings page shows
     */
    public String getMessageKey() {
        return messageKey;
    }

    /**
     * @return String the message's parameter, or null
     */
    public String getMessageParam() {
        return messageParam;
    }
}
