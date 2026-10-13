/*
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
package io.github.carlos_emr.carlos.billings.ca.on.validator;

import java.util.List;

/**
 * Provider configuration rejected before an OHIP generation pass writes any disk:
 * a stored billing group number that {@code BillingGroupNumber.normalize} could
 * not turn into the four-character MOH form (for example five digits, or a
 * letter mixed into a short value). Short all-digit values are padded, not
 * rejected.
 */
public class InvalidBillingGroupException extends BillingValidationException {
    private static final long serialVersionUID = 1L;

    private final List<String> providerNumbers;

    public InvalidBillingGroupException(List<String> providerNumbers) {
        super("Billing group number cannot be normalized to a four-character OHIP group number.");
        this.providerNumbers = List.copyOf(providerNumbers);
    }

    /** Provider identifiers only; internal exception details and claim data are not exposed. */
    public List<String> getProviderNumbers() {
        return providerNumbers;
    }
}
