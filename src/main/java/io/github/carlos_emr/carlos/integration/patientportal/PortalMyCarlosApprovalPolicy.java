/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import java.util.function.Function;

/**
 * How this clinic approves a patient's first myCarlos device (clinic code v1, mycarlos#141), set by
 * the CARLOS property {@value #PROPERTY}:
 * <ul>
 * <li>{@code code} (the default): CARLOS emails the patient a 9-digit code; the right code approves
 * the device, with no staff step;</li>
 * <li>{@code code_and_staff}: the code is emailed too, and after it matched staff also approve the
 * device on the patient's Portal page;</li>
 * <li>{@code staff_only}: staff approve every first device, as before.</li>
 * </ul>
 * A patient with no usable chart email, or whose email consent blocks sending, is approved by staff
 * whatever the setting. A re-link with an existing device's approval never needs a code.
 */
enum PortalMyCarlosApprovalPolicy {
    CODE("code"),
    CODE_AND_STAFF("code_and_staff"),
    STAFF_ONLY("staff_only");

    static final String PROPERTY = "mycarlos.approval.policy";

    private final String value;

    PortalMyCarlosApprovalPolicy(String value) {
        this.value = value;
    }

    /** @return the setting's value, also stored with each code */
    String value() {
        return value;
    }

    /** @return whether first devices are approved with an emailed code */
    boolean byCode() {
        return this != STAFF_ONLY;
    }

    static PortalMyCarlosApprovalPolicy fromCarlosProperties() {
        return from(PatientPortalSettings::rawProperty);
    }

    /**
     * @throws PatientPortalConfigurationException when the setting holds anything but one of the
     *         three values; an unset or blank setting is {@code code}
     */
    static PortalMyCarlosApprovalPolicy from(Function<String, String> lookup) {
        String raw = lookup.apply(PROPERTY);
        if (raw == null || raw.isBlank()) return CODE;
        String trimmed = raw.trim();
        for (PortalMyCarlosApprovalPolicy policy : values()) {
            if (policy.value.equals(trimmed)) return policy;
        }
        throw new PatientPortalConfigurationException(PROPERTY + " must be code, code_and_staff or staff_only");
    }
}
