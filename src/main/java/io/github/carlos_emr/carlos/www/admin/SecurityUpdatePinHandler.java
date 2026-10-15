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

    /**
     * Applies the Local/Remote PIN-lock checkboxes. The edit form disables those checkboxes
     * (and the PIN inputs) while Enable MFA is ticked, and browsers do not submit disabled
     * controls, so an absent value then means "not editable", not "cleared". Treating it as
     * unchecked would silently drop both flags, leaving the account password-only once MFA is
     * later disabled. With MFA enabled the stored flags are therefore kept as they are.
     *
     * @param security the already-authorized record being edited
     * @param enableMfa whether this save leaves Enable MFA ticked
     * @param localLockSet raw {@code b_LocalLockSet} value, or null when unchecked or disabled
     * @param remoteLockSet raw {@code b_RemoteLockSet} value, or null when unchecked or disabled
     * @throws NumberFormatException when a submitted flag is not an integer
     */
    public static void applyLockFlags(Security security, boolean enableMfa, String localLockSet, String remoteLockSet) {
        if (enableMfa) return;
        security.setBLocallockset(localLockSet == null ? 0 : Integer.parseInt(localLockSet));
        security.setBRemotelockset(remoteLockSet == null ? 0 : Integer.parseInt(remoteLockSet));
    }
}
