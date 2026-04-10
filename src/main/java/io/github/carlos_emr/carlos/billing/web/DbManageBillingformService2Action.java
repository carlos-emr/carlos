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
package io.github.carlos_emr.carlos.billing.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.Objects;

/**
 * Struts2 action to replace all service codes for a generic billing service type.
 *
 * <p>Replaces {@code billing/dbManageBillingform_service.jsp}. Deletes all existing
 * {@link CtlBillingService} rows for the given service type, then iterates over the
 * three groups ({@code Group1}–{@code Group3}) and up to 20 service entries per group,
 * persisting a new row for each non-empty {@code group{j}_service{i}} parameter.
 * Redirects to {@code manageBillingform.jsp} on success.
 *
 * @since 2026-04-05
 */
public class DbManageBillingformService2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private CtlBillingServiceDao ctlBillingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);

    /**
     * Replaces all service codes for the given generic billing service type.
     *
     * @return {@link #NONE} after redirecting, or if the request method is not POST
     * @throws SecurityException if the user lacks {@code _admin.billing} write privilege
     */
    @Override
    public String execute() throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        String typeid = Objects.toString(request.getParameter("typeid"), "");
        String type = Objects.toString(request.getParameter("type"), "");

        if (typeid.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing typeid parameter");
            return NONE;
        }

        try {
            // Delete all existing service entries for this service type
            for (CtlBillingService b : ctlBillingServiceDao.findByServiceType(typeid)) {
                ctlBillingServiceDao.remove(b.getId());
            }

            // Persist non-empty service entries across the three groups (j=1..3, i=0..19)
            for (int j = 1; j < 4; j++) {
                String groupName = Objects.toString(request.getParameter("group" + j), "");

                for (int i = 0; i < 20; i++) {
                    String serviceCode = request.getParameter("group" + j + "_service" + i);
                    if (serviceCode == null || serviceCode.isEmpty()) {
                        continue;
                    }
                    String orderStr = request.getParameter("group" + j + "_service" + i + "_order");
                    int serviceOrder = 0;
                    if (orderStr != null && !orderStr.isEmpty()) {
                        try {
                            serviceOrder = Integer.parseInt(orderStr);
                        } catch (NumberFormatException e) {
                            MiscUtils.getLogger().warn("Invalid serviceOrder value '{}' for group{}_service{} — defaulting to 0", LogSanitizer.sanitize(orderStr), j, i);
                            serviceOrder = 0;
                        }
                    }

                    CtlBillingService cbs = new CtlBillingService();
                    cbs.setServiceTypeName(type);
                    cbs.setServiceType(typeid);
                    cbs.setServiceCode(serviceCode);
                    cbs.setServiceGroupName(groupName);
                    cbs.setServiceGroup("Group" + j);
                    cbs.setStatus("A");
                    cbs.setServiceOrder(serviceOrder);
                    ctlBillingServiceDao.persist(cbs);
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to replace service codes for typeid={} — data may be inconsistent", typeid, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update service codes");
            return NONE;
        }

        response.sendRedirect(request.getContextPath() + "/billing/manageBillingform.jsp");
        return NONE;
    }
}
