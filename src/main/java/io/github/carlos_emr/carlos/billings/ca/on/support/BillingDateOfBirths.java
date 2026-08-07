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
package io.github.carlos_emr.carlos.billings.ca.on.support;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Helpers for working with the legacy {@code yyyyMMdd} DOB shape that the
 * Ontario billing tier stores on demographic presentation records.
 *
 * <p>This support helper stays outside the billing-form orchestrator so any
 * composer with a DOB string can use the age calculation without depending on
 * the orchestrator's full Spring graph.</p>
 *
 * @since 2026-04-27
 */
public final class BillingDateOfBirths {

    private BillingDateOfBirths() {}

    /**
     * Result of {@link #calculateAge}. {@code invalid == true} signals
     * a parse failure (caller renders a warning banner). The compact
     * constructor rejects the contradictory {@code invalid && age != 0}
     * state.
     */
    public record AgeResult(int age, boolean invalid) {
        public AgeResult {
            if (age < 0) {
                throw new IllegalArgumentException("age must be >= 0; got " + age);
            }
            if (invalid && age != 0) {
                throw new IllegalArgumentException(
                        "invalid=true must imply age==0; got age=" + age);
            }
        }
    }

    /**
     * Computes the age in years from a {@code yyyyMMdd} DOB string.
     *
     * <ul>
     *   <li>{@code null} or empty → "no DOB / no patient yet": {@code (0, false)}.</li>
     *   <li>length != 8 or non-numeric → parse failure: {@code (0, true)}.</li>
     *   <li>numerically valid but rejected by {@link LocalDate#of(int, int, int)}
     *       (e.g. {@code "99999999"}) → also parse failure.</li>
     * </ul>
     *
     * <p>Never logs the DOB itself (PHI) — only the input length / exception
     * type.</p>
     */
    public static AgeResult calculateAge(String dobYyyymmdd) {
        if (dobYyyymmdd == null || dobYyyymmdd.isEmpty()) {
            return new AgeResult(0, false);
        }
        if (dobYyyymmdd.length() != 8) {
            MiscUtils.getLogger().warn(
                    "BillingDateOfBirths.calculateAge: DOB has length {} (expected 8); flagging invalid",
                    dobYyyymmdd.length());
            return new AgeResult(0, true);
        }
        try {
            int year = Integer.parseInt(dobYyyymmdd.substring(0, 4));
            int month = Integer.parseInt(dobYyyymmdd.substring(4, 6));
            int day = Integer.parseInt(dobYyyymmdd.substring(6, 8));
            LocalDate dob = LocalDate.of(year, month, day);
            if (dob.isAfter(LocalDate.now())) {
                MiscUtils.getLogger().warn(
                        "BillingDateOfBirths.calculateAge: DOB is in the future; flagging invalid");
                return new AgeResult(0, true);
            }
            return new AgeResult(Period.between(dob, LocalDate.now()).getYears(), false);
        } catch (NumberFormatException | DateTimeException e) {
            MiscUtils.getLogger().warn(
                    "BillingDateOfBirths.calculateAge: 8-char DOB rejected by LocalDate.of ({}); flagging invalid",
                    e.getClass().getSimpleName());
            return new AgeResult(0, true);
        }
    }
}
