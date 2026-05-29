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

import io.github.carlos_emr.carlos.commn.dao.PreventionsLotNrsDao;
import io.github.carlos_emr.carlos.commn.model.PreventionsLotNrs;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Admin action for soft-deleting a prevention lot number record.
 *
 * <p>Requires {@code _admin w} privilege and POST method. Performs a logical
 * delete by setting the {@code deleted} flag on the {@link PreventionsLotNrs} record
 * and merging the change.</p>
 *
 * @since 2026-04-05
 */
public class LotNrDeleteRecord2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private PreventionsLotNrsDao preventionsLotNrsDao = SpringUtils.getBean(PreventionsLotNrsDao.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        String prevention = request.getParameter("prevention");
        String lotnr = request.getParameter("lotnr");

        request.setAttribute("prevention", prevention);

        if (prevention != null && lotnr != null) {
            try {
                PreventionsLotNrs record = preventionsLotNrsDao.findByName(prevention, lotnr, false);
                if (record != null) {
                    record.setDeleted(true);
                    preventionsLotNrsDao.merge(record);
                    request.setAttribute("resultMsg", "Lot number record deleted successfully.");
                } else {
                    request.setAttribute("resultMsg", "Lot number record not found.");
                }
            } catch (RuntimeException e) {
                MiscUtils.getLogger().error("Failed to delete lot number record", e);
                request.setAttribute("resultMsg", "Failed to delete lot number record.");
            }
        } else {
            request.setAttribute("resultMsg", "Both prevention type and lot number are required.");
        }

        return SUCCESS;
    }
}
