/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Answers a request with the CARLOS error page, the right HTTP status, and a short translated message
 * that says what went wrong and, where the user can do something about it, what to do.
 *
 * <p>The message is an {@code oscarResources} message key, set as the request attribute
 * {@value #ATTRIBUTE} before {@link HttpServletResponse#sendError(int)}. The container's error dispatch
 * keeps the request, so {@code errorpage.jsp} renders the message, HTML-encoded, above the status line.
 * Only server code chooses the key; nothing from the request is echoed. The status still comes from the
 * container's error handling, so the page is never rendered under a 4xx status by the action itself
 * (which ResponseSanitizationFilter would turn into a blank page).</p>
 *
 * @since 2026-10-08
 */
public final class ErrorPageMessage {

    /** The request attribute {@code errorpage.jsp} reads the message key from. */
    public static final String ATTRIBUTE = "carlosErrorMessageKey";

    private static final Pattern MESSAGE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    private ErrorPageMessage() {
    }

    /**
     * Sends {@code status} with the error page, which shows the message for {@code messageKey}.
     *
     * @param request    the request being answered; the key is set on it as {@value #ATTRIBUTE}
     * @param response   the response, not yet committed
     * @param status     an HTTP error status (400 or above)
     * @param messageKey a dotted {@code oscarResources} key, chosen by server code
     * @throws IOException if the error cannot be sent
     * @throws IllegalArgumentException if the status is not an error or the key is not a message key
     */
    public static void sendError(HttpServletRequest request, HttpServletResponse response, int status,
                                 String messageKey) throws IOException {
        if (status < HttpServletResponse.SC_BAD_REQUEST) {
            throw new IllegalArgumentException("not an error status: " + status);
        }
        if (messageKey == null || !MESSAGE_KEY.matcher(messageKey).matches()) {
            throw new IllegalArgumentException("not a message key");
        }
        request.setAttribute(ATTRIBUTE, messageKey);
        response.sendError(status);
    }
}
