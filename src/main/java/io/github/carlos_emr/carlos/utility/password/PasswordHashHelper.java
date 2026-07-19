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

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;


/**
 * A helper class for password hashing using Spring Security's password encoder.
 *
 * @see org.springframework.security.crypto.password.PasswordEncoder
 * @see <a href="https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html">
 * Spring Security Password Storage Documentation </a>
 */
public class PasswordHashHelper {

    private static final PasswordEncoder PASSWORD_ENCODER = PasswordHashHelper.getDelegatingPasswordEncoder();

    /**
     * Creates a {@link DelegatingPasswordEncoder} with default mappings.
     * Additional mappings may be added and the encoding will be updated to conform with best practices.
     * However, due to the nature of {@link DelegatingPasswordEncoder} the updates should not impact users.
     * The mappings current are:
     * <ul>
     *     <li>bcrypt - BCryptPasswordEncoder (Default for encoding)</li>
     *     <li>null (SHA) - Deprecated_SHA_PasswordEncoder (Old deprecated encoding)</li>
     * </ul>
     * <br>
     * The {@link DelegatingPasswordEncoder} allows for encoding passwords using various algorithms,
     * determined by prefixes in the encoded password string.
     *
     * <br>
     * <br><p>
     * Prior to Spring Security 5.0, the default PasswordEncoder was NoOpPasswordEncoder, which required plain-text passwords.
     * Based on the Password History section, you might expect that the default PasswordEncoder would now be something like BCryptPasswordEncoder.
     * However, this ignores three real world problems:
     * <ul>
     *      <li>Many applications use old password encodings that cannot easily migrate.</li>
     *      <li>The best practice for password storage will change again.</li>
     *      <li>As a framework, Spring Security cannot make breaking changes frequently.</li>
     * </ul>
     * Instead, Spring Security introduces {@link DelegatingPasswordEncoder}, which solves all the problems by:
     * <ul>
     *      <li>Ensuring that passwords are encoded by using the current password storage recommendations</li>
     *      <li>Allowing for validating passwords in modern and legacy formats</li>
     *      <li>Allowing for upgrading the encoding in the future</li>
     * </ul>
     * </p>
     * @see <a href="https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#authentication-password-storage-dpe">
     *     Reference Documentation</a>
     *
     * @return the {@link PasswordEncoder} to use
     */

    private static PasswordEncoder getDelegatingPasswordEncoder() {
        String encodingId = "bcrypt";
        Map<String, PasswordEncoder> encoders = new HashMap<>();

        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put(null, new Deprecated_SHA_PasswordEncoder());

        return new DelegatingPasswordEncoder(encodingId, encoders);
    }

    /**
     * Encodes a raw password using the configured {@code PASSWORD_ENCODER}.
     *
     * @param rawPassword The raw password to encode. Cannot be null.
     * @return The encoded password string.
     * @throws IllegalArgumentException If the rawPassword is null.
     */
    public static String encodePassword(CharSequence rawPassword) throws IllegalArgumentException {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    /**
     * Checks if a raw password matches an encoded password using the configured {@code PASSWORD_ENCODER}.
     *
     * @param rawPassword     The raw password to check. Cannot be null.
     * @param encodedPassword The encoded password to compare against. Cannot be null.
     * @return True if the raw password matches the encoded password, false otherwise.
     * @throws IllegalArgumentException if either rawPassword or encodedPassword is null.
     */
    public static boolean matches(CharSequence rawPassword, String encodedPassword) throws IllegalArgumentException {
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * Determines if a given encoded password requires updating to the current hashing algorithm.
     *
     * @param encodedPassword The encoded password to upgrade. Cannot be null.
     * @return True if the password was upgraded, false otherwise.
     * @throws IllegalArgumentException if encodedPassword is null.
     */
    public static boolean upgradeEncoding(String encodedPassword) {
        return PASSWORD_ENCODER.upgradeEncoding(encodedPassword);
    }

}
