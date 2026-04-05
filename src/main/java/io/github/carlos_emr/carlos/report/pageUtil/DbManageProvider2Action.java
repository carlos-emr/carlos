/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.report.pageUtil;

import java.util.Enumeration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.dao.ReportProviderDao;
import io.github.carlos_emr.carlos.commn.model.ReportProvider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts2 action for managing report provider lists.
 *
 * <p>Migrated from {@code oscarReport/dbManageProvider.jsp}. Accepts POST only and
 * enforces {@code _admin} write privilege. Deletes all {@link ReportProvider} entries
 * for the given {@code action}, then recreates them from the submitted provider
 * parameters (format {@code providerNo|group}).
 *
 * @since 2001-01-01
 */
public class DbManageProvider2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    private final HttpServletRequest request = ServletActionContext.getRequest();
    private final HttpServletResponse response = ServletActionContext.getResponse();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final ReportProviderDao reportProviderDao = SpringUtils.getBean(ReportProviderDao.class);

    /**
     * Replaces all report provider entries for the given action.
     *
     * <p>Deletes existing entries for {@code action}, then iterates over all request
     * parameters whose name contains {@code "provider"} to rebuild the list. Each
     * parameter value must be in {@code providerNo|group} format.
     *
     * @return {@link #SUCCESS} to forward to the view JSP, or {@link #NONE} if the
     *         method is not POST
     * @throws Exception if an unexpected error occurs during persistence
     */
    @Override
    public String execute() throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required security object: _admin");
        }

        String action = request.getParameter("action");

        // Remove all existing providers for this action
        List<ReportProvider> existing = reportProviderDao.findByAction(action);
        for (ReportProvider rp : existing) {
            reportProviderDao.remove(rp.getId());
        }

        // Recreate from submitted provider parameters (name must contain "provider")
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            if (!paramName.contains("provider")) {
                continue;
            }

            String paramValue = request.getParameter(paramName);
            int sepIdx = paramValue.indexOf("|");
            if (sepIdx < 0) {
                continue;
            }

            String providerNo = paramValue.substring(0, sepIdx);
            String myGroup = paramValue.substring(sepIdx + 1);

            ReportProvider rp = new ReportProvider();
            rp.setAction(action);
            rp.setProviderNo(providerNo);
            rp.setStatus("A");
            rp.setTeam(myGroup);
            reportProviderDao.persist(rp);
        }

        request.setAttribute("action", action);
        return SUCCESS;
    }
}
