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
package io.github.carlos_emr.carlos.billings.ca.report;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Request-parameter contract for the No-Show / Cancelled filter on the
 * unbilled-appointments billing report (Ontario {@code billingReportControl}
 * and British Columbia {@code billingReport_unbilled.jspf}).
 *
 * <p>Both statuses are <em>excluded</em> unless the matching checkbox is
 * submitted with the value {@code true}; an absent, blank or any other value
 * keeps the safe default so the report only lists work that still needs
 * billing (issue #3960). The filter UI follows open-osp/Open-O PR #134 / #186
 * (Chitrank Davé).</p>
 *
 * @since 2026-09-26
 */
public final class UnbilledReportStatusParameters {

    /** Checkbox name that opts No-Show ({@code N*}) appointments into the report. */
    public static final String INCLUDE_NO_SHOW = "includeNoShow";

    /** Checkbox name that opts Cancelled ({@code C*}) appointments into the report. */
    public static final String INCLUDE_CANCELLED = "includeCancelled";

    private UnbilledReportStatusParameters() {
    }

    /**
     * @param request current request, may be {@code null}
     * @return {@code true} only when {@value #INCLUDE_NO_SHOW} was submitted as {@code true}
     */
    public static boolean includeNoShow(HttpServletRequest request) {
        return isChecked(request, INCLUDE_NO_SHOW);
    }

    /**
     * @param request current request, may be {@code null}
     * @return {@code true} only when {@value #INCLUDE_CANCELLED} was submitted as {@code true}
     */
    public static boolean includeCancelled(HttpServletRequest request) {
        return isChecked(request, INCLUDE_CANCELLED);
    }

    private static boolean isChecked(HttpServletRequest request, String name) {
        // Exact match on the checkbox's value attribute: anything else (absent,
        // "on", "TRUE", garbage) falls back to the exclusion default.
        return request != null && "true".equals(request.getParameter(name));
    }
}
