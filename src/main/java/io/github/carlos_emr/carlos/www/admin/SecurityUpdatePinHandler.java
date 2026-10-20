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
     * Applies the Local/Remote PIN-lock checkboxes. A checkbox that is unticked is simply not
     * submitted, so absence alone cannot tell "cleared" from "not editable": the edit form
     * disables the checkboxes while Enable MFA is ticked and omits them entirely when the
     * legacy PIN section is not offered. The form therefore posts a marker field only while
     * the checkboxes are editable; without it the stored flags are kept, so enabling MFA
     * cannot silently drop them and leave the account password-only once MFA is disabled.
     *
     * @param security the already-authorized record being edited
     * @param flagsEditable whether the form posted the editable-checkboxes marker
     * @param localLockSet raw {@code b_LocalLockSet} value, or null when unticked
     * @param remoteLockSet raw {@code b_RemoteLockSet} value, or null when unticked
     * @throws NumberFormatException when a submitted flag is not an integer
     */
    public static void applyLockFlags(Security security, boolean flagsEditable, String localLockSet, String remoteLockSet) {
        if (!flagsEditable) return;
        security.setBLocallockset(localLockSet == null ? 0 : Integer.parseInt(localLockSet));
        security.setBRemotelockset(remoteLockSet == null ? 0 : Integer.parseInt(remoteLockSet));
    }
}
