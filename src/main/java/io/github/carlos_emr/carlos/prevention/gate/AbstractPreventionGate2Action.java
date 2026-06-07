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
package io.github.carlos_emr.carlos.prevention.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Shared superclass for the prevention-module JSP gate actions. Encapsulates
 * the privilege check and optional conditional-POST enforcement so each
 * concrete gate only declares the privilege type and (optionally) whether a
 * {@code formAction} parameter must arrive via POST.
 *
 * <p>Gate contract:
 * <ol>
 *   <li>Resolve {@link LoggedInInfo} from the session and check
 *       {@code _prevention} with the privilege type supplied by the subclass
 *       — throw {@link SecurityException} on failure.</li>
 *   <li>If {@link #requireConditionalPost()} returns {@code true} and the
 *       request carries a non-empty {@code formAction} parameter, require
 *       the HTTP method to be {@code POST} — otherwise return
 *       {@code HTTP 405 Method Not Allowed}. This closes the CSRF-via-GET
 *       vector for self-posting admin JSPs whose scriptlets mutate state
 *       when {@code formAction} is present.</li>
 *   <li>Return {@code SUCCESS}, which the Struts2 result mapping forwards
 *       to the gated JSP under {@code /WEB-INF/jsp/prevention/}.</li>
 * </ol>
 *
 * <p>Pattern reference: see the 2Action-gate series (#1109, #1629, #1632,
 * #1644, #1662, #1663, #1665). Keeping the boilerplate here resolves the
 * SonarCloud new-code duplication gate that would otherwise flag three
 * near-identical copies.
 *
 * @since 2026-04-13
 */
public abstract class AbstractPreventionGate2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Returns the privilege type the subclass requires on {@code _prevention}.
     *
     * @return {@code "r"} for view-only gates, {@code "w"} for mutation gates.
     */
    protected abstract String privilegeType();

    /**
     * Whether the gate should enforce POST when a {@code formAction} parameter
     * is present. Default is {@code false}; subclasses whose JSPs self-post
     * with scriptlet mutations override to {@code true}.
     *
     * @return {@code true} if conditional-POST enforcement is required.
     */
    protected boolean requireConditionalPost() {
        return false;
    }

    /**
     * Runs the privilege check and conditional-POST guard, then forwards to
     * the subclass's gated JSP via the Struts2 {@code SUCCESS} result.
     *
     * @return {@link #SUCCESS} when the request is authorized and well-formed,
     *         or {@link #NONE} after a {@code 405} has been written for a
     *         GET that attempted a mutation.
     * @throws SecurityException if the logged-in provider lacks the required
     *         {@code _prevention} privilege.
     * @throws Exception from the underlying servlet I/O when writing the 405.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public final String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_prevention", privilegeType(), null)) {
            throw new SecurityException("missing required sec object (_prevention)");
        }

        if (requireConditionalPost()) {
            String formAction = request.getParameter("formAction");
            if (formAction != null && !formAction.isEmpty()
                    && !"POST".equalsIgnoreCase(request.getMethod())) {
                HttpServletResponse response = ServletActionContext.getResponse();
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
                return NONE;
            }
        }

        return SUCCESS;
    }
}
