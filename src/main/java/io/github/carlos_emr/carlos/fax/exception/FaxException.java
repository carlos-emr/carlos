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
 * Base exception for fax operations.
 * Supports localized user-friendly error messages via resource bundles.
 *
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
public class FaxException extends RuntimeException {

    private String userMessageResourceKey = "fax.exception.defaultError";

    public FaxException(String message) {
        super(message);
    }

    public FaxException(String message, String userFriendlyMessage) {
        super(message);
        setUserMessageResourceKey(userFriendlyMessage);
    }

    public FaxException(Exception e) {
        super(e);
    }

    public FaxException(Exception e, String userFriendlyMessage) {
        super(e);
        setUserMessageResourceKey(userFriendlyMessage);
    }

    public String getUserMessageResourceKey() {
        return userMessageResourceKey;
    }

    public void setUserMessageResourceKey(String message) {
        userMessageResourceKey = message;
    }

    public String getUserFriendlyMessage(Locale locale) {
        try {
            ResourceBundle resourceBundle = ResourceBundle.getBundle("oscarResources", locale);
            return resourceBundle.getString(userMessageResourceKey);
        } catch (Exception e) {
            return userMessageResourceKey;
        }
    }

    public String getUserFriendlyMessage() {
        return getUserFriendlyMessage(Locale.getDefault());
    }
}
