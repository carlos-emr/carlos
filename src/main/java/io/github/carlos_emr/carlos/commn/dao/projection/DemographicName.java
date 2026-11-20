/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao.projection;

/** Only the patient fields needed to label a schedule appointment. */
public record DemographicName(Integer demographicNo, String lastName, String firstName) {
    /** Preserves the formatting of DemographicManager.getDemographicFormattedName. */
    public String formattedName() {
        return lastName + ", " + firstName;
    }
}
