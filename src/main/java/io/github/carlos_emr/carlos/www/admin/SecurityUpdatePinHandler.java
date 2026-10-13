/*
 * Copyright (c) 2026 CARLOS EMR Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.www.admin;

import java.util.Date;
import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.commn.model.Security;

/** Applies explicit PIN edits without changing credentials omitted by disabled form controls. */
public final class SecurityUpdatePinHandler {
    private SecurityUpdatePinHandler() { }

    /**
     * Preserves the PIN and its update date for omitted inputs and the edit-form sentinel.
     * Explicit edits retain the existing configurable legacy encoding behavior.
     *
     * @param security the already-authorized record being edited
     * @param submittedPin the raw form value, or null when the control was omitted
     * @param legacyEncoding whether the existing PIN encoding setting is enabled
     */
    public static void apply(Security security, String submittedPin, boolean legacyEncoding) {
        if (submittedPin == null || "****".equals(submittedPin)) return;
        security.setPin(legacyEncoding ? Misc.encryptPIN(submittedPin) : submittedPin);
        security.setPinUpdateDate(new Date());
    }
}
