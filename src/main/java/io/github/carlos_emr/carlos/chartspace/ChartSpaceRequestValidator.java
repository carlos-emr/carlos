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
package io.github.carlos_emr.carlos.chartspace;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Request checks shared by every ChartSpace action, in a fixed order that is
 * part of the security contract:
 *
 * <ol>
 *   <li>HTTP method must be GET or HEAD, else 405 with {@code Allow: GET, HEAD};</li>
 *   <li>a logged-in session must exist, else {@link SecurityException};</li>
 *   <li>{@code demographicNo} must parse (see {@link ChartSpaceParams}), else 400;</li>
 *   <li>{@code _eChart} read must be granted for that patient, else
 *       {@link SecurityException}.</li>
 * </ol>
 *
 * <p>Nothing before the privilege check touches data, and the 405 and 400
 * paths never reach {@link SecurityInfoManager}. Block-specific privileges
 * (for example {@code _allergy}) are checked by each block's loader, not here.</p>
 *
 * @since 2026-10-08
 */
@Component
public class ChartSpaceRequestValidator {

    private final SecurityInfoManager securityInfoManager;

    /**
     * @param securityInfoManager privilege checker
     */
    @Autowired
    public ChartSpaceRequestValidator(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * A request that passed every check.
     *
     * @param loggedInInfo the authenticated user
     * @param demographicNo the validated patient id
     */
    public record Validated(LoggedInInfo loggedInInfo, int demographicNo) {
    }

    /**
     * Runs the checks.
     *
     * @param request current request
     * @param response current response; receives the 405 or 400 when a check fails
     * @return the validated request, or empty when an error status was already
     *         written to {@code response} (the caller must then return {@code NONE})
     * @throws SecurityException if there is no session or {@code _eChart r} is missing
     * @throws IOException if writing the error status fails
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (HTTP method); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (HTTP method); not a security or authorization decision")
    public Optional<Validated> validate(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return Optional.empty();
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }

        OptionalInt demographicNo = ChartSpaceParams.parseDemographicNo(request.getParameter("demographicNo"));
        if (demographicNo.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return Optional.empty();
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eChart", "r", String.valueOf(demographicNo.getAsInt()))) {
            throw new SecurityException("missing required sec object (_eChart)");
        }

        return Optional.of(new Validated(loggedInInfo, demographicNo.getAsInt()));
    }
}
