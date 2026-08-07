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
 * Admin action for adding a prevention lot number record.
 *
 * <p>Requires {@code _admin w} privilege and POST method. Checks for an existing
 * record before inserting: restores a soft-deleted record if found, rejects a
 * duplicate active record, or creates a new {@link PreventionsLotNrs} entry otherwise.</p>
 *
 * @since 2026-04-05
 */
public class LotNrAddRecord2Action extends ActionSupport {

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
                // Check if a deleted record already exists — restore it
                PreventionsLotNrs deleted = preventionsLotNrsDao.findByName(prevention, lotnr, true);
                if (deleted != null) {
                    deleted.setDeleted(false);
                    preventionsLotNrsDao.merge(deleted);
                    request.setAttribute("resultMsg", "Lot number record restored successfully.");
                } else {
                    // Check for an already-active duplicate
                    PreventionsLotNrs active = preventionsLotNrsDao.findByName(prevention, lotnr, false);
                    if (active != null) {
                        request.setAttribute("resultMsg", "Duplicate: this lot number already exists for the selected prevention type.");
                    } else {
                        PreventionsLotNrs newRecord = new PreventionsLotNrs();
                        newRecord.setPreventionType(prevention);
                        newRecord.setLotNr(lotnr);
                        newRecord.setProviderNo(loggedInInfo.getLoggedInProviderNo());
                        preventionsLotNrsDao.persist(newRecord);
                        request.setAttribute("resultMsg", "Lot number record added successfully.");
                    }
                }
            } catch (RuntimeException e) {
                MiscUtils.getLogger().error("Failed to add lot number record", e);
                request.setAttribute("resultMsg", "Failed to add lot number record.");
            }
        } else {
            request.setAttribute("resultMsg", "Both prevention type and lot number are required.");
        }

        return SUCCESS;
    }
}
