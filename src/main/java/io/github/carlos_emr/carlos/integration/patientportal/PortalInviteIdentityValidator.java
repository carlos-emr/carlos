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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a demographic record can produce an activatable portal invite.
 *
 * <p>The portal stores only salted keyed hashes of email, date of birth, and health card number, and
 * requires the patient to reproduce all three at activation. An invite issued from an incomplete
 * record is therefore not merely rejected later — it is <b>permanently unactivatable</b>, and the
 * patient has no way to discover why.
 *
 * <p>Validating here rather than letting the portal answer {@code 422} is deliberate. The portal's
 * validation body is a structured list that can echo the offending input, so CARLOS discards it
 * rather than copy patient data into an error message. That means a portal-side rejection reaches
 * staff as "validation failed" with no field named. A record missing a health card number is an
 * ordinary situation in a clinic, and staff need to be told which field to fill in.
 *
 * <p>Date of birth is assembled from the three separate columns CARLOS stores it in. A record with a
 * partial or nonsensical date fails here rather than producing a silently wrong proof hash.
 *
 * @since 2026-08-19
 */
public class PortalInviteIdentityValidator {

    /** Field labels are staff-facing and deliberately name the field, never its value. */
    static final String MISSING_EMAIL = "email address";

    static final String MISSING_DATE_OF_BIRTH = "date of birth";
    static final String MISSING_HEALTH_CARD = "health card number";

    /**
     * The outcome of checking one demographic record.
     *
     * @param email patient email, when present
     * @param dateOfBirth assembled date of birth, when the three columns form a real date
     * @param healthCardNumber patient HIN/HCN, when present
     * @param missingFields staff-facing labels for what the record lacks; empty when usable
     */
    public record Result(
            String email, LocalDate dateOfBirth, String healthCardNumber,
            List<String> missingFields) {

        public Result {
            missingFields = List.copyOf(missingFields);
        }

        public boolean isUsable() {
            return missingFields.isEmpty();
        }
    }

    /**
     * Validates the identity proof a portal invite requires.
     *
     * @param demographic the patient record; may be {@code null} when the id resolved to nothing
     * @return a result naming every missing field, so staff fix the record once rather than
     *     discovering the fields one rejection at a time
     */
    public Result validate(Demographic demographic) {
        List<String> missing = new ArrayList<>();
        if (demographic == null) {
            missing.add(MISSING_EMAIL);
            missing.add(MISSING_DATE_OF_BIRTH);
            missing.add(MISSING_HEALTH_CARD);
            return new Result(null, null, null, missing);
        }

        String email = trimmedOrNull(demographic.getEmail());
        if (email == null) {
            missing.add(MISSING_EMAIL);
        }
        LocalDate dateOfBirth = dateOfBirth(demographic);
        if (dateOfBirth == null) {
            missing.add(MISSING_DATE_OF_BIRTH);
        }
        String healthCardNumber = trimmedOrNull(demographic.getHin());
        if (healthCardNumber == null) {
            missing.add(MISSING_HEALTH_CARD);
        }
        return new Result(email, dateOfBirth, healthCardNumber, missing);
    }

    /**
     * Assembles the date of birth CARLOS stores across three string columns.
     *
     * @return the date, or {@code null} if any part is absent or the three do not form a real date
     */
    private static LocalDate dateOfBirth(Demographic demographic) {
        String year = trimmedOrNull(demographic.getYearOfBirth());
        String month = trimmedOrNull(demographic.getMonthOfBirth());
        String day = trimmedOrNull(demographic.getDateOfBirth());
        if (year == null || month == null || day == null) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (NumberFormatException | DateTimeException exception) {
            // A partial or impossible date must not become a silently wrong proof hash.
            return null;
        }
    }

    private static String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
