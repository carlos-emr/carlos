//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.billings.ca.bc.pageUtil;

import javax.servlet.http.HttpServletRequest;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EditServiceCodeAssoc2Action extends ActionSupport {
    private String svcCode;
    private String mode;
    private ServiceCodeAssociation serviceCodeAssociation;
    private HttpServletRequest request = ServletActionContext.getRequest();

    // Action properties
    public String getSvcCode() {
        return svcCode;
    }

    public void setSvcCode(String svcCode) {
        this.svcCode = svcCode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public ServiceCodeAssociation getServiceCodeAssociation() {
        return serviceCodeAssociation;
    }

    @Override
    public String execute() {
        // Set mode to "edit"
        this.mode = "edit";

        // Retrieve the service code from request or action property
        if (svcCode == null || svcCode.isEmpty()) {
            svcCode = request.getParameter("svcCode") != null ? request.getParameter("svcCode") : "";
        }

        // Fetch ServiceCodeAssociation based on service code
        BillingAssociationPersistence per = new BillingAssociationPersistence();
        serviceCodeAssociation = per.getServiceCodeAssocByCode(svcCode);

        // Store mode and serviceCodeAssociation for the view
        request.setAttribute("mode", this.mode);
        request.setAttribute("serviceCodeAssociation", serviceCodeAssociation);

        return SUCCESS;
    }

    @Override
    public void validate() {
        if (svcCode == null || svcCode.trim().isEmpty()) {
            addFieldError("svcCode", "Service code cannot be empty.");
        }
    }
}
