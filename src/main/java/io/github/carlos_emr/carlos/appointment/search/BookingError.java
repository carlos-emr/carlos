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
 * Represents an error that occurred during the appointment booking process.
 *
 * <p>Contains an error code and a human-readable description to communicate
 * booking failures to the presentation layer.</p>
 *
 * @since 2026-03-17
 */
public class BookingError {
    private String code = null;
    private String description = null;

    /**
     * Constructs an empty booking error.
     */
    public BookingError() {
    }

    /**
     * Constructs a booking error with the specified code and description.
     *
     * @param code String the error code identifying the type of booking failure
     * @param description String the human-readable error description
     */
    public BookingError(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Returns the error code identifying the type of booking failure.
     *
     * @return String the error code
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the error code.
     *
     * @param code String the error code
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Returns the human-readable error description.
     *
     * @return String the error description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the human-readable error description.
     *
     * @param description String the error description
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
