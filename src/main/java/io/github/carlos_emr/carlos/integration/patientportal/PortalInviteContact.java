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
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteException.Reason;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import org.apache.commons.validator.routines.EmailValidator;

/**
 * What the portal needs from the chart to invite a patient: where to send the code, and the date of
 * birth and health card number the patient must confirm at activation.
 *
 * <p>{@link #from(Demographic)} checks all three before any portal call, so a chart that could never
 * complete activation is refused without preparing anything.
 *
 * @param email the chart email, stripped
 * @param dateOfBirth the chart date of birth
 * @param healthCardNumber the chart health card number, without spaces or dashes, upper case
 * @since 2026-09-22
 */
record PortalInviteContact(String email, LocalDate dateOfBirth, String healthCardNumber) {

    private static final int MIN_HEALTH_CARD_LENGTH = 4;

    /** @throws PortalInviteException when the chart lacks a usable email, date of birth or health card */
    static PortalInviteContact from(Demographic patient) {
        String email = strip(patient.getEmail());
        if (email.isEmpty()) {
            throw new PortalInviteException(Reason.MISSING_EMAIL);
        }
        if (!EmailValidator.getInstance().isValid(email)) {
            throw new PortalInviteException(Reason.INVALID_EMAIL);
        }
        LocalDate dateOfBirth;
        try {
            dateOfBirth = LocalDate.of(Integer.parseInt(strip(patient.getYearOfBirth())),
                    Integer.parseInt(strip(patient.getMonthOfBirth())),
                    Integer.parseInt(strip(patient.getDateOfBirth())));
        } catch (NumberFormatException | DateTimeException exception) {
            throw new PortalInviteException(Reason.INCOMPLETE_DATE_OF_BIRTH);
        }
        String healthCard = strip(patient.getHin()).replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (healthCard.length() < MIN_HEALTH_CARD_LENGTH) {
            throw new PortalInviteException(Reason.MISSING_HEALTH_CARD);
        }
        return new PortalInviteContact(email, dateOfBirth, healthCard);
    }

    /** Keeps the patient's details out of logs and exception traces. */
    @Override
    public String toString() {
        return "PortalInviteContact[redacted]";
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
