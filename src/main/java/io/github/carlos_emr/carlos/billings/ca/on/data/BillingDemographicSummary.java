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

    /** Empty / no-patient default. */
    public static final BillingDemographicSummary EMPTY =
            new BillingDemographicSummary("", "", "", "", "", "", "", "", "", "");

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
}
