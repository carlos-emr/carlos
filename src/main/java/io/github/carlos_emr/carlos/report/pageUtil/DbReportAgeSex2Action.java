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

import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.dao.ReportAgeSexDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts2 action for regenerating the age/sex report data.
 *
 * <p>Migrated from {@code oscarReport/dbReportAgeSex.jsp}. Accepts GET or POST and
 * enforces {@code _report} or {@code _admin.reporting} read privilege (matching the
 * original JSP security check). Deletes stale age/sex records, repopulates them from
 * the given year of birth floor, then forwards to the report view.
 *
 * @since 2026-04-05
 */
public class DbReportAgeSex2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    private final HttpServletRequest request = ServletActionContext.getRequest();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final ReportAgeSexDao reportAgeSexDao = SpringUtils.getBean(ReportAgeSexDao.class);

    /**
     * Deletes and repopulates age/sex report data, then forwards to the report view.
     *
     * @return {@code "success"} mapped to {@code /oscarReport/oscarReportAgeSex.jsp}
     * @throws Exception if an unexpected error occurs during data population
     */
    @Override
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.reporting", "r", null)) {
            throw new SecurityException("missing required security object (_report or _admin.reporting)");
        }

        reportAgeSexDao.deleteAllByDate(new Date());
        reportAgeSexDao.populateAll("1800");

        return SUCCESS;
    }
}
