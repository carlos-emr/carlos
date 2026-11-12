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

package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import jakarta.servlet.http.HttpServletRequest;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class EditServiceCodeAssoc2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private String svcCode;
    private String mode;
    private ServiceCodeAssociation serviceCodeAssociation;
    private HttpServletRequest request = ServletActionContext.getRequest();

    // Action properties
    public String getSvcCode() {
        return svcCode;
    }

    @StrutsParameter
    public void setSvcCode(String svcCode) {
        this.svcCode = svcCode;
    }

    public String getMode() {
        return mode;
    }

    @StrutsParameter
    public void setMode(String mode) {
        this.mode = mode;
    }

    public ServiceCodeAssociation getServiceCodeAssociation() {
        return serviceCodeAssociation;
    }

    /**
     * Opens the association editor. With the service code of an existing association it opens that association to
     * edit: the code is read-only and the save replaces its diagnoses. Without one (Create New Association) it opens
     * an empty editor in create mode, where the code can be typed and the save refuses a code already associated.
     *
     * <p>Every request used to be opened in edit mode, and an empty code failed validation into an unmapped result,
     * so the list's Create New Association link pointed back at the list (issue #4439 D14).</p>
     */
    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String requested = svcCode != null ? svcCode : request.getParameter("svcCode");
        String code = requested == null ? "" : requested.trim();
        ServiceCodeAssociation found = code.isEmpty() ? null
                : new BillingAssociationPersistence().getServiceCodeAssocByCode(code);
        // getServiceCodeAssocByCode names the code only when it has diagnoses; a code with none is a new association.
        boolean editing = found != null && found.getServiceCode() != null;
        this.mode = editing ? "edit" : "";
        this.serviceCodeAssociation = editing ? found : new ServiceCodeAssociation();

        request.setAttribute("mode", this.mode);
        request.setAttribute("serviceCodeAssociation", serviceCodeAssociation);
        return SUCCESS;
    }
}
