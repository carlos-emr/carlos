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
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

/**
 * What an import restored for one appointment from the sender's {@code
 * CARLOS.Appointment.Source.v1} record, after that record proved it belongs to the element.
 *
 * @param nativeEnd the prepared end is the sender's stored end clock (the CARLOS scheduler's
 *     inclusive end, which can be earlier than the start for a slot past midnight), not start
 *     plus Duration
 * @param completeNotes the prepared notes are the sender's complete notes text; the element
 *     held it cut at the CDS limit or without characters XML cannot hold
 * @param style {@code appointment.style} as the sender stored it, or null when not restored
 * @param billing {@code appointment.billing}
 * @param location {@code appointment.location}
 * @param resources {@code appointment.resources}
 * @param urgency {@code appointment.urgency}
 * @param type {@code appointment.type}
 * @param bookingSource {@code appointment.bookingSource}
 */
public record CdsAppointmentSourceDto(
        boolean nativeEnd,
        boolean completeNotes,
        String style,
        String billing,
        String location,
        String resources,
        String urgency,
        String type,
        String bookingSource) {
    /** Column capacities of the {@code appointment} table, in characters. */
    public static final int STYLE = 10,
            BILLING = 10,
            LOCATION = 30,
            RESOURCES = 255,
            URGENCY = 30,
            TYPE = 50,
            BOOKING_SOURCE = 32;

    public CdsAppointmentSourceDto {
        bounded(style, STYLE);
        bounded(billing, BILLING);
        bounded(location, LOCATION);
        bounded(resources, RESOURCES);
        bounded(urgency, URGENCY);
        bounded(type, TYPE);
        bounded(bookingSource, BOOKING_SOURCE);
    }

    private static void bounded(String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum)
            throw new IllegalArgumentException("APPOINTMENT_PREPARED_TEXT_INVALID");
    }

    @Override
    public String toString() {
        return "CdsAppointmentSourceDto[protected]";
    }
}
