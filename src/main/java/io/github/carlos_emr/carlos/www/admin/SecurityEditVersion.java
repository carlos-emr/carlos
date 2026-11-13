/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.www.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

import io.github.carlos_emr.carlos.commn.model.Security;

/**
 * The version of a login ({@code security} row) the Security Records edit form was rendered from.
 *
 * <p>The edit form ({@code securityupdatesecurity.jsp}) posts every control it shows, and the save
 * used to copy all of them onto the row. A form opened before another administrator's save
 * therefore put that administrator's change back without a warning: a Force password reset, an
 * expiry flag or date, a lock flag or the MFA switch was silently undone. The form now carries
 * this version as {@value #PARAMETER}; {@code SecurityRecordUpdateService} locks the row, computes
 * the version again and refuses a form that no longer matches.</p>
 *
 * <p>The version is a SHA-256 over the values the form shows and the save writes (user name,
 * provider number, expiry flag and date, both lock flags, Force password reset, the MFA switch)
 * and the row's three change times ({@code lastUpdateDate}, {@code passwordUpdateDate},
 * {@code pinUpdateDate}). The times alone are not enough: they are stored to the second, so two
 * saves within one second would look the same. The password hash, the PIN, the MFA secret and the
 * ONE ID values never enter the version; only the time a credential last changed does, so a
 * password or PIN changed elsewhere after the form was opened also makes the form stale.</p>
 *
 * <p>A post without the parameter (an older page, a hand-built request) is saved without the
 * check, as the other guarded edit forms do.</p>
 *
 * @since 2026-11-13
 */
public final class SecurityEditVersion {

    /** The request parameter, and hidden edit-form field, that carries the version. */
    public static final String PARAMETER = "securityVersion";

    private SecurityEditVersion() { }

    /**
     * @param version the posted {@value #PARAMETER} value
     * @return whether the form carried a version to compare
     */
    public static boolean isPresent(String version) {
        return version != null && !version.isBlank();
    }

    /**
     * Whether a posted version names the stored row as it is now.
     *
     * @param version the posted {@value #PARAMETER} value; must be {@linkplain #isPresent present}
     * @param security the row as currently stored
     * @return {@code true} when the form was rendered from the row as it is now
     */
    public static boolean matches(String version, Security security) {
        return isPresent(version) && version.trim().equals(of(security));
    }

    /**
     * Computes the version of a login as stored.
     *
     * @param security the {@code security} row
     * @return an opaque 64-character token that changes whenever a value the edit form shows or
     *         writes, or one of the row's change times, changes; it carries no credential
     */
    public static String of(Security security) {
        String state = String.join("\u0000",
                text(security.getUserName()),
                text(security.getProviderNo()),
                String.valueOf(security.getBExpireset()),
                millis(security.getDateExpiredate()),
                String.valueOf(security.getBLocallockset()),
                String.valueOf(security.getBRemotelockset()),
                String.valueOf(security.isForcePasswordReset()),
                Boolean.toString(security.isUsingMfa()),
                millis(security.getLastUpdateDate()),
                millis(security.getPasswordUpdateDate()),
                millis(security.getPinUpdateDate()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", impossible);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String millis(Date date) {
        return date == null ? "" : Long.toString(date.getTime());
    }
}
