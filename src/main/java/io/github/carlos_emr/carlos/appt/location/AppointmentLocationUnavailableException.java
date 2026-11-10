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

/** A booking selected a location that is no longer available; no appointment may be saved. */
public final class AppointmentLocationUnavailableException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public static final String MESSAGE_KEY = "appointment.location.unavailable";

    public AppointmentLocationUnavailableException() {
        // Fixed text for non-localized callers; never expose posted location or patient data.
        super("The selected appointment location is no longer available. Nothing was saved. Return to the appointment form and choose a location.");
    }
}
