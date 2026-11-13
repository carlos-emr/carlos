/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.admin.service;

/**
 * What the Security Records edit form ({@code securityupdatesecurity.jsp}) posted for one login,
 * already validated by {@code SecurityUpdate2Action} (user name pattern, password policy).
 *
 * <p>Values keep the form's own conventions, which {@link SecurityRecordUpdateService} applies as
 * the save always has: {@code password} equal to
 * {@code SecurityUpdatePasswordValidator.UNCHANGED_PASSWORD} keeps the stored hash; a {@code pin}
 * that is {@code null} (control disabled or absent) or {@code "****"} keeps the stored PIN; the
 * lock flags are applied only when {@code lockFlagsEditable} says the form offered them.</p>
 *
 * <p>The record carries a raw password and PIN, so {@link #toString()} names neither: it must be
 * safe to reach a log line by accident.</p>
 *
 * @param securityNo        the login's {@code security_no}
 * @param userName          the validated, trimmed user name
 * @param providerNo        the provider number the form posted
 * @param expireSet         the expiry flag ({@code b_ExpireSet}), 0 when unticked
 * @param expireDate        the raw {@code date_ExpireDate} (read as {@code MyDateFormat.getSysDate} reads it)
 * @param lockFlagsEditable whether the form posted its {@code lockFlagsEditable} marker
 * @param localLockSet      raw {@code b_LocalLockSet}, or {@code null} when unticked
 * @param remoteLockSet     raw {@code b_RemoteLockSet}, or {@code null} when unticked
 * @param password          the raw submitted password, or the unchanged marker
 * @param pin               the raw submitted PIN, the unchanged marker, or {@code null}
 * @param forcePasswordReset whether Force password reset was posted as true
 * @param usingMfa          whether Enable MFA was posted ticked
 * @param renderedVersion   the posted {@code securityVersion}, or {@code null} for an unversioned post
 * @since 2026-11-13
 */
public record SecurityRecordUpdateCommand(
        int securityNo,
        String userName,
        String providerNo,
        int expireSet,
        String expireDate,
        boolean lockFlagsEditable,
        String localLockSet,
        String remoteLockSet,
        String password,
        String pin,
        boolean forcePasswordReset,
        boolean usingMfa,
        String renderedVersion) {

    /** Names the record only; never the credentials it carries. */
    @Override
    public String toString() {
        return "SecurityRecordUpdateCommand[securityNo=" + securityNo + ", credentials redacted]";
    }
}
