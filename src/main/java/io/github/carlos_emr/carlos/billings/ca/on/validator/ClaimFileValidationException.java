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

/**
 * The OHIP claim file for one provider could not be built to the ministry's
 * record layout (a missing date of birth, a value wider than its field, a
 * non-ASCII character, a record of the wrong length). Thrown before anything
 * is written for that provider so the operator corrects the data instead of
 * submitting a batch the ministry would reject whole.
 *
 * @since 2026-10-08
 */
public class ClaimFileValidationException extends BillingValidationException {

    private static final long serialVersionUID = 1L;

    private final String providerNo;
    private final String details;

    public ClaimFileValidationException(String providerNo, String details) {
        super("Provider " + providerNo + ": the OHIP claim file could not be built: " + details
                + " Nothing was written for this provider.");
        this.providerNo = providerNo;
        this.details = details;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public String getDetails() {
        return details;
    }
}
