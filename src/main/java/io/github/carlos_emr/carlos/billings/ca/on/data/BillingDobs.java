/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Helpers for working with the legacy {@code yyyyMMdd} DOB shape that the
 * Ontario billing tier stores ({@link BillingDemographicSummary#dob()}).
 *
 * <p>Lives next to {@link BillingDemographicSummary} rather than on the
 * billing-form orchestrator so the age calculation is reachable from
 * any composer that has a DOB string and doesn't need to depend on the
 * orchestrator's full Spring graph.</p>
 *
 * @since 2026-04-27
 */
public final class BillingDobs {

    private BillingDobs() {}

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
                    "BillingDobs.calculateAge: DOB has length {} (expected 8); flagging invalid",
                    dobYyyymmdd.length());
            return new AgeResult(0, true);
        }
        try {
            int year = Integer.parseInt(dobYyyymmdd.substring(0, 4));
            int month = Integer.parseInt(dobYyyymmdd.substring(4, 6));
            int day = Integer.parseInt(dobYyyymmdd.substring(6, 8));
            LocalDate dob = LocalDate.of(year, month, day);
            return new AgeResult(Period.between(dob, LocalDate.now()).getYears(), false);
        } catch (NumberFormatException | DateTimeException e) {
            MiscUtils.getLogger().warn(
                    "BillingDobs.calculateAge: 8-char DOB rejected by LocalDate.of ({}); flagging invalid",
                    e.getClass().getSimpleName());
            return new AgeResult(0, true);
        }
    }
}
