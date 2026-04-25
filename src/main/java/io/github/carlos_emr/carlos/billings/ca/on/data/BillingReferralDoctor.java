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

/**
 * Immutable referral-doctor triple shared across the ON billing view models.
 * Replaces the {@code (referralDoctor / referralDoctorName,
 * referralDoctorOhip, referralSpecialty)} field cluster that recurs in
 * {@link BillingONFormViewModel}, {@link BillingONReviewViewModel}, and
 * {@link BillingShortcutPg1ViewModel}.
 *
 * <p>Each component is null-coalesced to empty so EL output via
 * {@code <carlos:encode>} doesn't render the literal {@code "null"} string.
 * The {@code specialty} field is empty for view models that don't carry it
 * (Review, ShortcutPg1) — only {@link BillingONFormViewModel} populates it.</p>
 *
 * @since 2026-04-25
 */
public record BillingReferralDoctor(
        String name,
        String ohip,
        String specialty) {

    /** Empty / no-referral default. */
    public static final BillingReferralDoctor EMPTY =
            new BillingReferralDoctor("", "", "");

    /** Compact constructor coalesces nulls to empty. */
    public BillingReferralDoctor {
        name = name == null ? "" : name;
        ohip = ohip == null ? "" : ohip;
        specialty = specialty == null ? "" : specialty;
    }
}
