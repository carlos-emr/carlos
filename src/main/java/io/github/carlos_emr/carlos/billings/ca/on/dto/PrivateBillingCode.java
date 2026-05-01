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
package io.github.carlos_emr.carlos.billings.ca.on.dto;

/**
 * A private (clinic-defined) billing code paired with its description.
 *
 * <p>Replaces a flat {@code List<String>} that interleaved
 * {@code (serviceCode, description)} pairs and forced every consumer to
 * iterate by twos with positional offsets.</p>
 *
 * @param serviceCode the OHIP-style code with the leading {@code "_"} marker
 *                    that identifies it as private; never null
 * @param description the human-readable description; never null
 * @since 2026-05-01
 */
public record PrivateBillingCode(String serviceCode, String description) {
    public PrivateBillingCode {
        serviceCode = serviceCode == null ? "" : serviceCode;
        description = description == null ? "" : description;
    }
}
