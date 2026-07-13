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
 * One service-line input row flowing through the OHIP billing-review pipeline:
 * the user-supplied service code, its unit count, and the {@code servicedAt}
 * indicator pulled from the review form. Replaces the prior shape of three
 * parallel {@code ArrayList<String>} columns indexed by position.
 *
 * @param code        the OHIP service code (e.g. {@code A001A}); never null,
 *                    coerced to "" by the canonical constructor
 * @param unit        unit count as the form-string the JSP submitted; never null
 * @param servicedAt  the OHIP "serviced-at" indicator; never null
 * @since 2026-05-01
 */
public record BillingReviewServiceParam(String code, String unit, String servicedAt) {
    public BillingReviewServiceParam {
        code = code == null ? "" : code;
        unit = unit == null ? "" : unit;
        servicedAt = servicedAt == null ? "" : servicedAt;
    }
}
