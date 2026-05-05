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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.support;

/**
 * One service-line row in the BillingShortcutPg2 calculation pipeline:
 * the OHIP service code, its description, the unit count, and the per-unit
 * price. Replaces the four parallel {@code ArrayList<String>} columns
 * (codes / descriptions / units / prices) that the legacy code maintained
 * in lockstep at every populate / read / pop site.
 *
 * <p>Crosses one boundary into
 * {@link io.github.carlos_emr.carlos.billings.ca.on.service.BillingClaimSubmissionService#getBillingClaimHospObj}
 * which previously took the three (codes, units, prices) lists separately
 * and dropped the description on the floor.</p>
 *
 * @param code        the OHIP service code; never null, coerced to ""
 * @param description human-readable code description; never null
 * @param unit        unit count as the form-string the JSP submitted; never null
 * @param price       per-unit price as a decimal-string; never null
 * @since 2026-05-01
 */
public record BillingServiceLine(String code, String description, String unit, String price) {
    public BillingServiceLine {
        code = code == null ? "" : code;
        description = description == null ? "" : description;
        unit = unit == null ? "" : unit;
        price = price == null ? "" : price;
    }
}
