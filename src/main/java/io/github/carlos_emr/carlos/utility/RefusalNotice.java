/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sends a 4xx refusal whose reason the error page shows the user.
 *
 * <p>{@code sendError}'s message never reaches the user: {@code errorpage.jsp} renders only the
 * status and an incident id unless developer detail is enabled, so refusals such as "Report is too
 * large" or "The legacy encounter editor is retired" appeared as a bare "CARLOS Error: 410"
 * (#5897). A refusal sent through here also publishes a resource-bundle key, which the error page
 * renders for 4xx statuses. Only a key chosen by application code is published, never request
 * text, so the page cannot echo patient data or an attacker's input.</p>
 *
 * @since 2026-11-19
 */
public final class RefusalNotice {

    /** Request attribute holding the bundle key of the refusal's user-facing reason. */
    public static final String KEY_ATTRIBUTE = "carlosRefusalKey";

    private RefusalNotice() {
    }

    /**
     * Publishes the reason's bundle key and sends the error status.
     *
     * @param request the request the error page renders for
     * @param response the response to refuse
     * @param status a client-error status, 400 to 499
     * @param messageKey the oscarResources key of the user-facing reason
     * @param logMessage the container's error message (English, PHI-free), kept for logs and clients
     * @throws IOException if sending the error fails
     * @throws IllegalArgumentException if the status is not a client error
     */
    public static void send(HttpServletRequest request, HttpServletResponse response, int status,
                            String messageKey, String logMessage) throws IOException {
        if (status < 400 || status > 499) {
            throw new IllegalArgumentException("A refusal notice needs a 4xx status");
        }
        request.setAttribute(KEY_ATTRIBUTE, messageKey);
        response.sendError(status, logMessage);
    }
}
