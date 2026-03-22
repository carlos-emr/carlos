/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.appointment.search;

import java.util.Calendar;

/**
 * Transfer object representing the confirmation details of a booked appointment.
 *
 * <p>Encapsulates appointment time, description, provider name, location, and any
 * booking errors that may have occurred during the scheduling process. Used to
 * communicate appointment confirmation data between the booking engine and the
 * presentation layer.</p>
 *
 * @since 2026-03-17
 */
public class AppointmentConfirmationTransfer {
    private Calendar appointmentTime = null;
    private Calendar appointmentEndTime = null;
    private String appointmentDescription = null;
    private String providerName = null;
    private String location = null;

    private BookingError bookingError = null;

    /**
     * Constructs an empty appointment confirmation transfer object.
     */
    public AppointmentConfirmationTransfer() {
    }

    /**
     * Constructs an appointment confirmation with the specified details.
     *
     * @param appointmentTime Calendar the scheduled appointment start time
     * @param appointmentDescription String description of the appointment
     * @param providerName String name of the healthcare provider
     * @param location String location where the appointment will take place
     */
    public AppointmentConfirmationTransfer(Calendar appointmentTime, String appointmentDescription, String providerName, String location) {
        this.appointmentTime = appointmentTime;
        this.appointmentDescription = appointmentDescription;
        this.providerName = providerName;
        this.location = location;
    }

    /**
     * Constructs an appointment confirmation with start time, end time, and other details.
     *
     * @param appointmentTime Calendar the scheduled appointment start time
     * @param endTime Calendar the scheduled appointment end time
     * @param appointmentDescription String description of the appointment
     * @param providerName String name of the healthcare provider
     * @param location String location where the appointment will take place
     */
    public AppointmentConfirmationTransfer(Calendar appointmentTime, Calendar endTime, String appointmentDescription, String providerName, String location) {
        this.appointmentTime = appointmentTime;
        this.appointmentEndTime = endTime;
        this.appointmentDescription = appointmentDescription;
        this.providerName = providerName;
        this.location = location;
    }

    /**
     * Constructs an appointment confirmation representing a booking failure.
     *
     * @param bookingError BookingError the error that occurred during booking
     */
    public AppointmentConfirmationTransfer(BookingError bookingError) {
        this.bookingError = bookingError;
    }

    /**
     * Returns the scheduled appointment start time.
     *
     * @return Calendar the appointment start time, or {@code null} if not set
     */
    public Calendar getAppointmentTime() {
        return appointmentTime;
    }

    /**
     * Returns the description of the appointment.
     *
     * @return String the appointment description, or {@code null} if not set
     */
    public String getAppointmentDescription() {
        return appointmentDescription;
    }

    /**
     * Returns the name of the healthcare provider for this appointment.
     *
     * @return String the provider name, or {@code null} if not set
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Returns the booking error if the appointment could not be confirmed.
     *
     * @return BookingError the booking error, or {@code null} if the booking succeeded
     */
    public BookingError getBookingError() {
        return bookingError;
    }

    /**
     * Sets the scheduled appointment start time.
     *
     * @param appointmentTime Calendar the appointment start time
     */
    public void setAppointmentTime(Calendar appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    /**
     * Sets the description of the appointment.
     *
     * @param appointmentDescription String the appointment description
     */
    public void setAppointmentDescription(String appointmentDescription) {
        this.appointmentDescription = appointmentDescription;
    }

    /**
     * Sets the name of the healthcare provider.
     *
     * @param providerName String the provider name
     */
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    /**
     * Sets the booking error for this confirmation.
     *
     * @param bookingError BookingError the error that occurred during booking
     */
    public void setBookingError(BookingError bookingError) {
        this.bookingError = bookingError;
    }

    /**
     * Returns the scheduled appointment end time.
     *
     * @return Calendar the appointment end time, or {@code null} if not set
     */
    public Calendar getAppointmentEndTime() {
        return appointmentEndTime;
    }

    /**
     * Sets the scheduled appointment end time.
     *
     * @param appointmentEndTime Calendar the appointment end time
     */
    public void setAppointmentEndTime(Calendar appointmentEndTime) {
        this.appointmentEndTime = appointmentEndTime;
    }

    /**
     * Returns the location where the appointment will take place.
     *
     * @return String the appointment location, or {@code null} if not set
     */
    public String getLocation() {
        return location;
    }

    /**
     * Sets the location where the appointment will take place.
     *
     * @param location String the appointment location
     */
    public void setLocation(String location) {
        this.location = location;
    }
}
