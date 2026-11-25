/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

/**
 * A demographic update named a stored extension that is not one of the patient's.
 *
 * <p>Raised alike for an id that no longer exists and for an id of another patient's extension,
 * and it carries neither the id nor the other patient: the caller is not told which it was.
 * Nothing has been written when it is thrown.</p>
 *
 * @since 2026-11-19
 */
public final class DemographicExtensionNotFoundException extends IllegalArgumentException {
    public DemographicExtensionNotFoundException() {
        super("A submitted extension is not a stored extension of this patient.");
    }
}
