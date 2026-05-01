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
 * One provider dropdown entry — identifying ids plus the billing-group
 * context the OHIP submission flow needs.
 *
 * <p>Replaces a {@code List<String>} of pipe-delimited 6-tuples that
 * {@link io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService}
 * used to emit (e.g. {@code "999998|Smith|Jane|012345|0000|00"}). Every
 * consumer had to {@code split("\\|")} and read positional offsets; the
 * record makes the contract explicit and removes one stringly-typed hop.</p>
 *
 * @since 2026-05-01
 */
public record ProviderDropdownEntry(
        String providerNo,
        String lastName,
        String firstName,
        String ohipNo,
        String billingGroupNo,
        String specialtyCode) {
    public ProviderDropdownEntry {
        providerNo = providerNo == null ? "" : providerNo;
        lastName = lastName == null ? "" : lastName;
        firstName = firstName == null ? "" : firstName;
        ohipNo = ohipNo == null ? "" : ohipNo;
        billingGroupNo = billingGroupNo == null ? "" : billingGroupNo;
        specialtyCode = specialtyCode == null ? "" : specialtyCode;
    }
}
