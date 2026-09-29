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
package io.github.carlos_emr.carlos.eform.actions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds the POST/redirect/GET destination for the patient and independent eForm
 * delete/restore actions ({@link RemEForm2Action}, {@link UnRemEForm2Action}).
 *
 * <p>Those actions used to <em>forward</em> to the list pages. A forward keeps the POST
 * method, and the list gate ({@code ViewEFormPage2Action}) only accepts GET/HEAD there, so
 * every successful delete or restore ended on "CARLOS Error: 405" even though the change had
 * been saved. A redirect starts a new GET, so the list context the forward used to carry
 * implicitly (patient, group, calling popup, sort order) must be copied into the query string.
 *
 * <p>Only the parameters the destination page reads are echoed, each URL-encoded, so the
 * target stays an application-relative path and request data cannot add parameters, change
 * the path, or smuggle a {@code ${...}} expression into the Struts redirect result.
 *
 * @since 2026-09-28
 */
final class EFormListRedirect {

    private EFormListRedirect() {
    }

    /**
     * Returns {@code path} with the named request parameters appended.
     *
     * @param path application-relative list route, e.g. {@code /eform/efmpatientformlist}
     * @param request the delete/restore POST whose list context is carried over
     * @param contextParams the parameters the destination list page reads; absent or empty
     *                      ones are skipped
     * @return the redirect location, never null
     */
    static String to(String path, HttpServletRequest request, String... contextParams) {
        StringBuilder target = new StringBuilder(path);
        char separator = '?';
        for (String name : contextParams) {
            String value = request.getParameter(name);
            if (value == null || value.isEmpty()) {
                continue;
            }
            target.append(separator)
                    .append(name)
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            separator = '&';
        }
        return target.toString();
    }
}
