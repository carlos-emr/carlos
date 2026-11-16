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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.CarlosProperties;
import jakarta.servlet.http.HttpServletResponse;

/** Deployment guard for pages that require the BC billing schema. */
final class BcBillingRequestGuards {

    private BcBillingRequestGuards() {
    }

    /**
     * Refuses unavailable pages before an action or its JSP queries BC-only tables.
     * Only the deployment property selects the schema; request parameters cannot override it.
     */
    // billregion is a two-letter deployment code; case-insensitive matching preserves configured BC values.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "billregion is a two-letter deployment code")
    static boolean requireBcProvince(HttpServletResponse response) throws IOException {
        String region = CarlosProperties.getInstance().getProperty("billregion", "").trim();
        if ("BC".equalsIgnoreCase(region)) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return false;
    }
}
