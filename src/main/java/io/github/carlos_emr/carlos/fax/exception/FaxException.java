/**
 * Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for
 * CloudPractice Inc.
 * Victoria, British Columbia
 * Canada
 *
 * Ported to CARLOS EMR from JunoEMR (2026).
 */
package io.github.carlos_emr.carlos.fax.exception;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Base exception for all fax-related operations in the CARLOS EMR fax module.
 * <p>
 * Supports localized, user-friendly error messages via Java {@link ResourceBundle}.
 * Each exception carries a resource key that maps to a translatable message in
 * the {@code oscarResources} bundle. If the key cannot be resolved, the raw key
 * string is returned as a fallback.
 * <p>
 * Subclasses specialize for connection errors ({@link FaxApiConnectionException}),
 * validation errors ({@link FaxApiValidationException}), and number format issues
 * ({@link FaxNumberException}).
 *
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
public class FaxException extends RuntimeException {

    /** Resource bundle key for the user-facing error message. */
    private String userMessageResourceKey = "fax.exception.defaultError";

    /**
     * Constructs a new FaxException with the specified detail message.
     *
     * @param message String the technical detail message for logging
     */
    public FaxException(String message) {
        super(message);
    }

    /**
     * Constructs a new FaxException with a detail message and a user-friendly resource key.
     *
     * @param message String the technical detail message for logging
     * @param userFriendlyMessage String the resource bundle key for the user-facing message
     */
    public FaxException(String message, String userFriendlyMessage) {
        super(message);
        setUserMessageResourceKey(userFriendlyMessage);
    }

    /**
     * Constructs a new FaxException wrapping a cause exception.
     *
     * @param e Exception the underlying cause
     */
    public FaxException(Exception e) {
        super(e);
    }

    /**
     * Constructs a new FaxException wrapping a cause exception with a user-friendly resource key.
     *
     * @param e Exception the underlying cause
     * @param userFriendlyMessage String the resource bundle key for the user-facing message
     */
    public FaxException(Exception e, String userFriendlyMessage) {
        super(e);
        setUserMessageResourceKey(userFriendlyMessage);
    }

    /**
     * Returns the resource bundle key for the user-facing error message.
     *
     * @return String the resource key
     */
    public String getUserMessageResourceKey() {
        return userMessageResourceKey;
    }

    /**
     * Sets the resource bundle key for the user-facing error message.
     *
     * @param message String the resource key to set
     */
    public void setUserMessageResourceKey(String message) {
        userMessageResourceKey = message;
    }

    /**
     * Resolves and returns the localized user-friendly error message.
     * Falls back to returning the raw resource key if bundle lookup fails.
     *
     * @param locale Locale the locale for message resolution
     * @return String the localized user-friendly message
     */
    public String getUserFriendlyMessage(Locale locale) {
        try {
            ResourceBundle resourceBundle = ResourceBundle.getBundle("oscarResources", locale);
            return resourceBundle.getString(userMessageResourceKey);
        } catch (Exception e) {
            // Fall back to the raw key if the resource bundle lookup fails
            return userMessageResourceKey;
        }
    }

    /**
     * Resolves and returns the user-friendly error message using the default locale.
     *
     * @return String the user-friendly message in the default locale
     */
    public String getUserFriendlyMessage() {
        return getUserFriendlyMessage(Locale.getDefault());
    }
}
