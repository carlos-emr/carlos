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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.io.IOException;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Small HTTP-method guard helpers shared by the migrated Ontario billing
 * Struts actions.
 *
 * <p>The older actions relied on framework defaults and scattered inline
 * checks. Centralizing the POST guard keeps the write-action contract obvious
 * and consistent across the remaining multi-method actions.</p>
 */
final class BillingRequestGuards {

    private static final String POST = "POST";

    private BillingRequestGuards() {
    }

    /**
     * Enforce a POST-only mutation contract, returning a 405 when the request
     * method is wrong.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    static boolean requirePost(HttpServletRequest request, HttpServletResponse response) {
        if (POST.equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        response.setHeader("Allow", POST);
        try {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        } catch (IOException e) {
            MiscUtils.getLogger().info(e.toString());
        }
        return false;
    }
}
