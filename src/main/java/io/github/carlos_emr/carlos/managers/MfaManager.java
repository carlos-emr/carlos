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

package io.github.carlos_emr.carlos.managers;

import org.jboss.aerogear.security.otp.api.Base32;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.OscarProperties;

import java.io.UnsupportedEncodingException;

/**
 * This interface provides methods for managing Multi-Factor Authentication (MFA) within the OSCAR EMR system.
 * It provides methods for generating MFA secrets, creating TOTP URLs, generating QR code data, and managing
 * the association of MFA secrets with user sec record.
 */
public interface MfaManager {

    String TOTP_URL_FORMAT = "otpauth://totp/%s:%s?secret=%s&issuer=%s";
    String MFA_ENABLE_PROPERTY = "mfa.enabled";
    String MFA_LEGACY_PIN_ENABLE = "mfa.legacy.pin.enable";

    /**
     * Checks if Multi-Factor Authentication (MFA) is enabled in the OSCAR system.
     *
     * @return true if MFA is enabled, false otherwise.
     */
    static boolean isOscarMfaEnabled() {
        return Boolean.parseBoolean(OscarProperties.getInstance().getProperty(MFA_ENABLE_PROPERTY));
    }

    /**
     * Checks if legacy PIN authentication is enabled in the OSCAR system.
     *
     * @return true if legacy PIN authentication is enabled, false otherwise.
     */
    static boolean isOscarLegacyPinEnabled() {
        return Boolean.parseBoolean(OscarProperties.getInstance()
                .getProperty(MFA_LEGACY_PIN_ENABLE, String.valueOf(!MfaManager.isOscarMfaEnabled())));
    }

    /**
     * Generates a random MFA secret using Base32 encoding.
     *
     * @return A randomly generated MFA secret string.
     */
    static String generateMfaSecret() {
        return Base32.random();
    }

    /**
     * Determines if MFA registration is required for a given sec ID.
     *
     * @param securityId The ID of the sec record to check.
     * @return true if MFA registration is required, false otherwise.
     * @throws IllegalStateException if the system is in an invalid state for checking MFA registration.
     */
    boolean isMfaRegistrationRequired(Integer securityId) throws IllegalStateException;

    /**
     * Generates a Time-based One-Time Password (TOTP) URL for a given user.
     *
     * @param email   The user's email address.
     * @param secret  The MFA secret associated with the user.
     * @param appName The name of the application or service.
     * @return A TOTP URL string.
     * @throws UnsupportedEncodingException if the URL encoding is not supported.
     */
    String getTotpUrl(String email, String secret, String appName) throws UnsupportedEncodingException;

    /**
     * Generates QR code image data for a given user's MFA setup.
     *
     * @param email   The user's email address.
     * @param secret  The MFA secret associated with the user.
     * @param appName The name of the application or service.
     * @return A byte array representing the QR code image data.
     */
    byte[] getQRCodeImageData(String email, String secret, String appName);

    /**
     * Generates QR code image data for a given sec ID and MFA secret.
     *
     * @param securityId The ID of the sec record.
     * @param secret     The MFA secret associated with the user.
     * @return A string representing the QR code image data.
     */
    String getQRCodeImageData(Integer securityId, String secret);

    /**
     * Saves an MFA secret for a given sec record.
     * @param loggedInInfo information about the logged in user
     * @param security the sec object
     * @param mfaSecret the mfa secret
     * @throws Exception if an error occurs
     */
    void saveMfaSecret(LoggedInInfo loggedInInfo, Security security, String mfaSecret) throws Exception;

    /**
     * Retrieves the MFA secret associated with a given sec record.
     *
     * @param security The sec object representing the user's sec record.
     * @return The MFA secret associated with the sec record.
     * @throws Exception If an error occurs during the process of retrieving the MFA secret.
     */
    String getMfaSecret(Security security) throws Exception;

    /**
     * Resets the MFA secret for a given sec record.
     * @param loggedInInfo Information about the logged-in user.
     * @param security The sec object representing the user's sec record.
     */
    void resetMfaSecret(LoggedInInfo loggedInInfo, Security security);
}
