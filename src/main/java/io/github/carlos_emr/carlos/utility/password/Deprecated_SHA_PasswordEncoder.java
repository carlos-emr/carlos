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
 */

package io.github.carlos_emr.carlos.utility.password;


import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.MessageDigest;


/**
 * This class uses insecure SHA hashing for backwards compatibility while migrating to a newer hashing method.
 * It will be removed in the future.
 */
public class Deprecated_SHA_PasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        try {
            return this.encodeShaPassword(rawPassword.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private String encodeShaPassword(String password) throws Exception {

        MessageDigest md = MessageDigest.getInstance("SHA");

        StringBuilder sbTemp = new StringBuilder();
        byte[] btNewPasswd = md.digest(password.getBytes());
        for (int i = 0; i < btNewPasswd.length; i++) sbTemp = sbTemp.append(btNewPasswd[i]);

        return sbTemp.toString();

    }
}
