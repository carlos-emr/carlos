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

package io.github.carlos_emr.carlos.tickler.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.owasp.encoder.Encode;

/**
 * Struts2 action that migrates the server-side logic from {@code tickler/dbTicklerDemoMain.jsp}.
 *
 * <p>Handles bulk status updates for patient-level tickler listings. When checkbox values
 * are present, the action loops over each selected tickler ID, determines the target
 * {@link Tickler.STATUS} from the first character of {@code submit_form}, and delegates to
 * {@link TicklerManager#updateStatus}. Control is then redirected to {@code ticklerMain.jsp}
 * carrying the original view parameters.
 *
 * <p>When no checkboxes are submitted the action redirects immediately to
 * {@code ticklerMain.jsp} with the current view parameters unchanged.
 *
 * <p>Security: requires {@code _tickler} read privilege.
 *
 * @since 2026-01-01
 */
public final class DbTicklerDemoMain2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Security check — requires _tickler read privilege
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "r", null)) {
            response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_tickler");
            return NONE;
        }

        String demoview = request.getParameter("demoview") == null ? "all" : request.getParameter("demoview");
        String parentAjaxId = request.getParameter("parentAjaxId") == null ? "" : request.getParameter("parentAjaxId");
        String updateParent = request.getParameter("updateParent") == null ? "false" : request.getParameter("updateParent");

        String redirectUrl = request.getContextPath() + "/tickler/ticklerMain.jsp"
                + "?demoview=" + Encode.forUriComponent(demoview)
                + "&parentAjaxId=" + Encode.forUriComponent(parentAjaxId)
                + "&updateParent=" + Encode.forUriComponent(updateParent);

        String[] checkboxes = request.getParameterValues("checkbox");
        if (checkboxes == null) {
            // No ticklers selected — just return to the listing view
            response.sendRedirect(redirectUrl);
            return NONE;
        }

        // Determine the requested status from the first character of submit_form
        String submitForm = request.getParameter("submit_form");
        Tickler.STATUS status = Tickler.STATUS.A;
        if (submitForm != null && !submitForm.isEmpty()) {
            char firstChar = submitForm.charAt(0);
            if (firstChar == 'C' || firstChar == 'c') {
                status = Tickler.STATUS.C;
            } else if (firstChar == 'D' || firstChar == 'd') {
                status = Tickler.STATUS.D;
            }
        }

        for (String ticklerIdStr : checkboxes) {
            try {
                Tickler t = ticklerManager.getTickler(loggedInInfo, Integer.parseInt(ticklerIdStr));
                if (t != null) {
                    ticklerManager.updateStatus(loggedInInfo, t.getId(),
                            loggedInInfo.getLoggedInProviderNo(), status);
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Invalid tickler checkbox value: " + ticklerIdStr, e);
            }
        }

        response.sendRedirect(redirectUrl);
        return NONE;
    }
}
