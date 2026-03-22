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

/**
 * Encapsulates the results of an appointment availability search.
 *
 * <p>Contains an array of available appointment options, an encrypted pagination token
 * for fetching additional results, and any booking error that may have occurred during
 * the search.</p>
 *
 * @since 2026-03-17
 */
public class AppointmentResults {
    private String nextStartDateEncrypted = null;
    private AppointmentOptionTransfer[] appointmentOptions = null;
    private BookingError bookingError = null;

    /**
     * Constructs an empty appointment results object.
     */
    public AppointmentResults() {
    }

    /**
     * Constructs an appointment results object representing a search failure.
     *
     * @param bookingError BookingError the error that occurred during the search
     */
    public AppointmentResults(BookingError bookingError) {
        this.bookingError = bookingError;
    }

    /**
     * Constructs a successful appointment results object with available options.
     *
     * @param nextStartDateEncrypted String encrypted pagination token for the next page of results
     * @param appointmentOptions AppointmentOptionTransfer[] array of available appointment options
     */
    public AppointmentResults(String nextStartDateEncrypted, AppointmentOptionTransfer[] appointmentOptions) {
        this.nextStartDateEncrypted = nextStartDateEncrypted;
        this.appointmentOptions = appointmentOptions;
    }

    /**
     * Returns the encrypted pagination token for fetching additional results.
     *
     * @return String the encrypted next start date token, or {@code null} if no more results
     */
    public String getNextStartDateEncrypted() {
        return nextStartDateEncrypted;
    }

    /**
     * Returns the array of available appointment options.
     *
     * @return AppointmentOptionTransfer[] the available appointment options, or {@code null} if none
     */
    public AppointmentOptionTransfer[] getAppointmentOptions() {
        return appointmentOptions;
    }

    /**
     * Returns the booking error, if any.
     *
     * @return BookingError the error, or {@code null} if the search succeeded
     */
    public BookingError getBookingError() {
        return bookingError;
    }

    /**
     * Sets the encrypted pagination token.
     *
     * @param nextStartDateEncrypted String the encrypted next start date token
     */
    public void setNextStartDateEncrypted(String nextStartDateEncrypted) {
        this.nextStartDateEncrypted = nextStartDateEncrypted;
    }

    /**
     * Sets the array of available appointment options.
     *
     * @param appointmentOptions AppointmentOptionTransfer[] the appointment options
     */
    public void setAppointmentOptions(AppointmentOptionTransfer[] appointmentOptions) {
        this.appointmentOptions = appointmentOptions;
    }

    /**
     * Sets the booking error.
     *
     * @param bookingError BookingError the error that occurred
     */
    public void setBookingError(BookingError bookingError) {
        this.bookingError = bookingError;
    }

}
