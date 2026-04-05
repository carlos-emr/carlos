/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.PreventionsLotNrsDao;
import io.github.carlos_emr.carlos.commn.model.PreventionsLotNrs;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Admin action for adding a prevention lot number record.
 *
 * <p>Requires {@code _admin w} privilege and POST method. Checks for an existing
 * record before inserting: restores a soft-deleted record if found, rejects a
 * duplicate active record, or creates a new {@link PreventionsLotNrs} entry otherwise.</p>
 *
 * @since 2026-05-01
 */
public class LotNrAddRecord2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private PreventionsLotNrsDao preventionsLotNrsDao = SpringUtils.getBean(PreventionsLotNrsDao.class);

    @Override
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(405);
            return NONE;
        }

        String prevention = request.getParameter("prevention");
        String lotnr = request.getParameter("lotnr");

        request.setAttribute("prevention", prevention);

        if (prevention != null && lotnr != null) {
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
        }

        return SUCCESS;
    }
}
