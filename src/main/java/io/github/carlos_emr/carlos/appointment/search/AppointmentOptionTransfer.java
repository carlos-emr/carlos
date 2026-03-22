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
 * Transfer object representing a single available appointment option presented to the patient.
 *
 * <p>Contains the available time, provider name, formatted time display string, an encrypted
 * string for secure booking reference, and a cancellation flag.</p>
 *
 * @since 2026-03-17
 */
public class AppointmentOptionTransfer {
    private Calendar availableTime = null;
    private String encString = null;
    private String providerName = null;
    private String timeDisplay = null;
    private boolean isCancelled;

    /**
     * Constructs an empty appointment option transfer object.
     */
    public AppointmentOptionTransfer() {
    }


    /**
     * Returns the human-readable formatted time display string.
     *
     * @return String the formatted time for display purposes
     */
    public String getTimeDisplay() {
        return timeDisplay;
    }


    /**
     * Constructs an appointment option with the specified details.
     *
     * @param availableTime Calendar the available appointment time
     * @param timeDisplay String the human-readable formatted time
     * @param providerName String the name of the healthcare provider
     * @param encString String the encrypted booking reference string
     */
    public AppointmentOptionTransfer(Calendar availableTime, String timeDisplay, String providerName, String encString) {
        this.availableTime = availableTime;
        this.encString = encString;
        this.providerName = providerName;
        this.timeDisplay = timeDisplay;
    }


    /**
     * Returns the available appointment time.
     *
     * @return Calendar the available time slot
     */
    public Calendar getAvailableTime() {
        return availableTime;
    }

    /**
     * Returns the encrypted booking reference string used to securely identify this time slot.
     *
     * @return String the encrypted reference string
     */
    public String getEncString() {
        return encString;
    }

    /**
     * Returns the name of the healthcare provider.
     *
     * @return String the provider name
     */
    public String getProviderName() {
        return providerName;
    }


    /**
     * Sets the available appointment time.
     *
     * @param availableTime Calendar the available time slot
     */
    public void setAvailableTime(Calendar availableTime) {
        this.availableTime = availableTime;
    }


    /**
     * Sets the encrypted booking reference string.
     *
     * @param encString String the encrypted reference string
     */
    public void setEncString(String encString) {
        this.encString = encString;
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
     * Sets the human-readable formatted time display string.
     *
     * @param timeDisplay String the formatted time for display
     */
    public void setTimeDisplay(String timeDisplay) {
        this.timeDisplay = timeDisplay;
    }


    /**
     * Returns whether this appointment option has been cancelled.
     *
     * @return boolean {@code true} if the appointment option is cancelled
     */
    public boolean isCancelled() {
        return isCancelled;
    }


    public void setCancelled(boolean isCancelled) {
        this.isCancelled = isCancelled;
    }
}
