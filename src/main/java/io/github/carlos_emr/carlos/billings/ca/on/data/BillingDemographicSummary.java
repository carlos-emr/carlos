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

import io.github.carlos_emr.carlos.commn.model.Demographic;

/**
 * Immutable demographic snapshot shared across the ON billing view models.
 * Replaces the {@code (demoFirst, demoLast, demoHin, demoVer, demoSex,
 * demoHcType, demoDob, demoDobYy, demoDobMm, demoDobDd)} field cluster that
 * was duplicated in 4 of the 5 page view models (Form, Review,
 * ShortcutPg1, Correction's bill-loaded path).
 *
 * <p>The view models retain their individual String getters for JSP-EL
 * back-compatibility ({@code ${reviewModel.demoFirst}} etc.) and additionally
 * expose a {@code getDemographicSummary()} accessor that returns one of these
 * records. Future code can prefer the structured view; legacy JSPs continue
 * to compile against the flat getters.</p>
 *
 * <p>Every component is null-coalesced to empty so EL output via
 * {@code <carlos:encode>} doesn't render the literal 4-character word
 * {@code "null"}. The {@code dob} field is the YYYYMMDD form; the
 * {@code dobYy / dobMm / dobDd} components are pre-split convenience copies
 * that the legacy JSPs already consume.</p>
 *
 * @since 2026-04-25
 */
public record BillingDemographicSummary(
        String firstName,
        String lastName,
        String hin,
        String ver,
        String sex,
        String hcType,
        String dob,
        String dobYy,
        String dobMm,
        String dobDd) {

    /** Empty / no-patient default. {@code hcType} defaults to {@code "ON"}
     *  to match the legacy JSP scriptlet behaviour: any empty/short hcType
     *  falls back to "ON" regardless of whether a demographic was loaded. */
    public static final BillingDemographicSummary EMPTY =
            new BillingDemographicSummary("", "", "", "", "", "ON", "", "", "", "");

    /**
     * Compact constructor coalesces nulls to empty so consumers don't render
     * the literal {@code "null"} string in patient banners.
     */
    public BillingDemographicSummary {
        firstName = firstName == null ? "" : firstName;
        lastName = lastName == null ? "" : lastName;
        hin = hin == null ? "" : hin;
        ver = ver == null ? "" : ver;
        sex = sex == null ? "" : sex;
        hcType = hcType == null ? "" : hcType;
        dob = dob == null ? "" : dob;
        dobYy = dobYy == null ? "" : dobYy;
        dobMm = dobMm == null ? "" : dobMm;
        dobDd = dobDd == null ? "" : dobDd;
    }

    /**
     * Canonical projection from a {@link Demographic} model — replaces the
     * five separate inline projections that previously lived in
     * {@code BillingONFormDemographicStep}, {@code BillingONReviewDataAssembler},
     * {@code BillingShortcutPg1DataAssembler},
     * {@code BillingCorrectionReviewDataAssembler}, and the legacy JSP
     * scriptlets that drove them.
     *
     * <p>Conventions encoded here:</p>
     * <ul>
     *   <li><b>HC type</b>: empty / shorter than 2 characters defaults to
     *       {@code "ON"}; otherwise truncated to the leading 2 characters and
     *       upper-cased. Matches the legacy
     *       {@code billingON.jsp} / {@code billingONReview.jsp} top-scriptlet
     *       behaviour.</li>
     *   <li><b>DOB</b>: zero-padded {@code YYYYMMDD}. The split components
     *       ({@code dobYy} / {@code dobMm} / {@code dobDd}) carry the raw
     *       year + 2-digit month + 2-digit day. Empty strings remain empty
     *       (the {@code dob} concatenation will be shorter than 8 chars and
     *       the page-level validator will flag it).</li>
     *   <li><b>Sex</b>: pass-through — no normalization to {@code "1"}/{@code "2"}.
     *       The Form page normalizes via its own helper since it defaults to
     *       {@code "1"} on empty/unknown; other pages keep raw {@code "M"} /
     *       {@code "F"}. Encoding the choice here would force one of those
     *       contracts on every caller.</li>
     *   <li><b>Other strings</b>: null-coalesced to empty per the record
     *       compact constructor.</li>
     * </ul>
     *
     * @param d {@link Demographic} entity (typically loaded via
     *          {@code DemographicDao.getDemographic}); may be {@code null}, in
     *          which case {@link #EMPTY} is returned.
     * @return immutable summary record.
     */
    public static BillingDemographicSummary fromDemographic(Demographic d) {
        if (d == null) {
            return EMPTY;
        }
        String hcType = d.getHcType();
        String canonicalHcType = (hcType == null || hcType.length() < 2)
                ? "ON"
                : hcType.substring(0, 2).toUpperCase();

        String dobYy = nullToEmpty(d.getYearOfBirth());
        String dobMm = padTwo(nullToEmpty(d.getMonthOfBirth()));
        String dobDd = padTwo(nullToEmpty(d.getDateOfBirth()));
        String dob = dobYy + dobMm + dobDd;

        return new BillingDemographicSummary(
                d.getFirstName(),
                d.getLastName(),
                d.getHin(),
                d.getVer(),
                d.getSex(),
                canonicalHcType,
                dob,
                dobYy,
                dobMm,
                dobDd);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String padTwo(String s) {
        return s.length() == 1 ? "0" + s : s;
    }
}
