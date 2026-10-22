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
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Admin action for permanently deleting a security (user account) record.
 *
 * <p>Requires either {@code _admin w} or {@code _admin.userAdmin w} privilege and POST method.
 * Performs a hard delete via {@link SecurityDao} and logs the action for the audit trail.</p>
 *
 * @since 2026-04-05
 */
@Component(SecurityDelete2Action.SPRING_BEAN_NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SecurityDelete2Action extends ActionSupport {

    public static final String SPRING_BEAN_NAME =
        "securityDelete2Action";

    private static final Logger logger = MiscUtils.getLogger();

    private final transient SecurityDao securityDao;
    private final transient CarlosMethodSecurity methodSecurity;

    /**
     * Creates the Spring-managed action.
     *
     * <p>This action uses constructor injection instead of the older
     * {@code SpringUtils} service-locator style. That keeps security and DAO
     * wiring explicit and easier to verify in tests.</p>
     *
     * @param securityDao the DAO used to find and remove security records
     * @param methodSecurity the helper that evaluates the shared admin write policy
     */
    public SecurityDelete2Action(SecurityDao securityDao, CarlosMethodSecurity methodSecurity) {
        this.securityDao = securityDao;
        this.methodSecurity = methodSecurity;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            logger.warn("Rejected security delete request with method {} from {}",
                    LogSafe.sanitize(String.valueOf(request.getMethod())),
                    LogSafe.sanitize(String.valueOf(request.getRemoteAddr())));
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!methodSecurity.hasAdminWrite()) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        String securityNoStr = request.getParameter("keyword");
        if (securityNoStr != null && !securityNoStr.isEmpty()) {
            executeDelete(request, loggedInInfo, securityNoStr);
        } else {
            request.setAttribute("msg", "No security identifier was provided.");
        }

        return SUCCESS;
    }

    /**
     * Parses the security ID, locates the entity, removes it, and sets a feedback message.
     *
     * @param loggedInInfo the current session context
     * @param securityNoStr the raw security-number parameter value
     */
    private void executeDelete(HttpServletRequest request, LoggedInInfo loggedInInfo, String securityNoStr) {
        try {
            int securityNo = Integer.parseInt(securityNoStr);
            Security entity = securityDao.find(securityNo);
            if (entity != null) {
                String userName = entity.getUserName();
                try {
                    securityDao.remove(entity);
                } catch (RuntimeException e) {
                    MiscUtils.getLogger().error("Failed to delete security entry", e);
                    request.setAttribute("msg", "Failed to delete security entry.");
                    return;
                }
                try {
                    LogAction.addLog(
                        loggedInInfo.getLoggedInProviderNo(),
                        LogConst.DELETE,
                        LogConst.CON_SECURITY,
                        securityNoStr,
                        request.getRemoteAddr()
                    );
                } catch (RuntimeException e) {
                    logger.error("Audit log failed after security entry deletion", e);
                    request.setAttribute("msg", "Security entry was deleted, but audit logging failed. Escalate for review.");
                    return;
                }
                request.setAttribute("msg", "Security entry deleted for user: ".concat(userName));
            } else {
                request.setAttribute("msg", "Security entry not found.");
            }
        } catch (NumberFormatException e) {
            request.setAttribute("msg", "Invalid security identifier.");
        }
    }
}
