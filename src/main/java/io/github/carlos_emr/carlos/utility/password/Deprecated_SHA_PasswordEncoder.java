/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.utility.password;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.MessageDigest;


/**
 * Verifies legacy unprefixed SHA-1 password hashes while accounts migrate to BCrypt.
 * This encoder must not create new SHA-1 hashes; new password creation is handled by
 * {@link PasswordHashHelper} through Spring Security's BCrypt encoder.
 *
 * @deprecated Legacy verification adapter retained for pre-BCrypt password rows only.
 * Do not use for new password hashes; use {@link PasswordHashHelper}.
 */
@Deprecated(since = "2026-06-20", forRemoval = false)
@SuppressWarnings("java:S1133") // Sonar: removal depends on completion of legacy password migration.
public class Deprecated_SHA_PasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        throw new UnsupportedOperationException(
                "Legacy SHA-1 password hashes are verification-only; use PasswordHashHelper for new hashes");
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return this.validateShaPassword(rawPassword.toString(), encodedPassword);
    }

    private boolean validateShaPassword(String newPassword, String existingPassword) {

        try {
            String p1 = this.encodeShaPassword(newPassword);
            if (p1.equals(existingPassword)) {
                return true;
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return false;
    }

    // FindSecBugs WEAK_MESSAGE_DIGEST_SHA1: compatibility verifier for legacy unprefixed password rows;
    // legacy SHA-1 creation is rejected and BCrypt remains the password write path.
    @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_SHA1",
            justification = "SHA-1 retained only to verify legacy unprefixed password rows; "
                    + "encode() rejects SHA-1 creation and new hashes use BCrypt")
    private String encodeShaPassword(String password) throws Exception {

        MessageDigest md = MessageDigest.getInstance("SHA");

        StringBuilder sbTemp = new StringBuilder();
        byte[] btNewPasswd = md.digest(password.getBytes());
        for (int i = 0; i < btNewPasswd.length; i++) sbTemp = sbTemp.append(btNewPasswd[i]);

        return sbTemp.toString();

    }
}
