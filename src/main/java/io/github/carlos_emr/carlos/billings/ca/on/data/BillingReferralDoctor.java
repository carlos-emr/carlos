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

import io.github.carlos_emr.SxmlMisc;

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

    /**
     * Canonical projection from a demographic's {@code FamilyDoctor} XML
     * blob — replaces the duplicated extraction in
     * {@code BillingONReviewDataAssembler.populateDemographicAndValidation}
     * and {@code BillingShortcutPg1DataAssembler.loadDemographic}.
     *
     * <p>The legacy convention: a {@code null} blob means "no family doctor
     * on file", which the Review/Shortcut JSPs render as the literal
     * {@code "N/A"} name + {@code "000000"} OHIP placeholder. A non-null
     * blob has the doctor name in the {@code <rd>} element and the OHIP
     * billing number in {@code <rdohip>}.</p>
     *
     * @param familyDoctorXml the {@code Demographic.familyDoctor} XML blob
     *                        (nullable).
     * @return record with name + OHIP populated; {@code specialty} stays
     *         empty since the FamilyDoctor blob doesn't carry it (Review
     *         and Shortcut don't display specialty either; the Form page
     *         resolves specialty separately via {@code SpecialistsDao}).
     */
    public static BillingReferralDoctor fromFamilyDoctor(String familyDoctorXml) {
        if (familyDoctorXml == null) {
            return new BillingReferralDoctor("N/A", "000000", "");
        }
        return new BillingReferralDoctor(
                SxmlMisc.getXmlContent(familyDoctorXml, "rd"),
                SxmlMisc.getXmlContent(familyDoctorXml, "rdohip"),
                "");
    }
}
