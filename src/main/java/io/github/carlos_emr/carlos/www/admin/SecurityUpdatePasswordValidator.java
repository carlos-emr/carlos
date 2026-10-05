/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.www.admin;

import java.nio.charset.StandardCharsets;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.PasswordPolicy;

/** Password validation before any Security Records edit is applied. */
public final class SecurityUpdatePasswordValidator {
    /** Matches the other account-password forms without truncating entered text. */
    public static final int MAX_PASSWORD_LENGTH = 32;
    /** BCrypt's input limit applies to UTF-8 bytes, not Java string length. */
    public static final int MAX_PASSWORD_BYTES = 72;
    public static final String UNCHANGED_PASSWORD = "*********";

    private SecurityUpdatePasswordValidator() { }

    /**
     * Validates the raw, untrimmed value and confirmation. The unchanged sentinel
     * bypasses complexity checks so editing flags never requires a new password.
     *
     * @param password submitted value, including the unchanged sentinel
     * @param confirmation submitted confirmation
     * @param properties configured password-complexity requirements
     * @return a localization key on rejection, otherwise null; never credential data
     */
    public static String validate(String password, String confirmation, CarlosProperties properties) {
        if (password == null || password.isEmpty()) return "admin.securityaddsecurity.msgPasswordInvalid";
        if (!password.equals(confirmation)) return "admin.securityrecord.msgPasswordNotConfirmed";
        if (UNCHANGED_PASSWORD.equals(password)) return null;
        if (password.length() > MAX_PASSWORD_LENGTH) return "admin.securityupdate.msgPasswordTooLong";
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            return "admin.securityupdate.msgPasswordEncodingTooLong";
        }
        return PasswordPolicy.validate(password, properties).isValid()
                ? null : "admin.securityaddsecurity.msgPasswordInvalid";
    }
}
