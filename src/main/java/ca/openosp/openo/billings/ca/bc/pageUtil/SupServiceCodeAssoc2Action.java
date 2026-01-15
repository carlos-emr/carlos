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

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.billings.ca.bc.data.SupServiceCodeAssocDAO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Struts 2 action for managing British Columbia supplementary service code associations.
 * <p>
 * This action handles the association between primary and secondary billing service codes
 * in the BC MSP (Medical Services Plan) billing system. Service code associations define
 * which supplementary codes can be billed together with primary service codes.
 * <p>
 * Operations supported:
 * <ul>
 * <li>View - Display list of service code associations</li>
 * <li>Edit - Create or update service code associations</li>
 * <li>Delete - Remove service code associations</li>
 * </ul>
 * <p>
 * All properties are automatically populated by Struts 2 property injection.
 *
 * @since 2006-04-20
 */
public class SupServiceCodeAssoc2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Main execution method that handles service code association operations.
     * <p>
     * Based on the actionMode property, this method:
     * <ul>
     * <li>MODE_VIEW - Lists all service code associations (default)</li>
     * <li>MODE_EDIT - Validates and saves/updates a service code association</li>
     * <li>MODE_DELETE - Deletes a service code association</li>
     * </ul>
     * <p>
     * For edit and delete operations, redirects back to the association list page
     * after processing. Validation errors are displayed via action errors.
     *
     * Expected request parameters (via Struts 2 property injection):
     * <ul>
     * <li>actionMode - String operation mode ("view", "edit", or "delete")</li>
     * <li>primaryCode - String primary service code (for edit)</li>
     * <li>secondaryCode - String secondary/supplementary service code (for edit)</li>
     * <li>id - String association ID (for delete)</li>
     * </ul>
     *
     * @return String "success" to forward to list view, or NONE after redirect
     * @throws RuntimeException if redirect fails
     */
    public String execute() {
        SupServiceCodeAssocDAO dao = SpringUtils.getBean(SupServiceCodeAssocDAO.class);
        if (!MODE_VIEW.equals(this.getActionMode())) {
            if (validateForm()) {
                try {
                    response.sendRedirect(request.getContextPath() + "/billing/CA/BC/billingSVCTrayAssoc.jsp");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return NONE;
            } else {
                if (MODE_DELETE.equals(this.getActionMode())) {
                    dao.deleteServiceCodeAssociation(this.getId());
                } else if (MODE_EDIT.equals(this.getActionMode())) {
                    dao.saveOrUpdateServiceCodeAssociation(this.getPrimaryCode(),
                            this.getSecondaryCode());
                }
            }
        }

        request.setAttribute("list", dao.getServiceCodeAssociactions());
        return SUCCESS;
    }

    /**
     * Validates the service code association form data.
     * <p>
     * For MODE_EDIT operations, validates that:
     * <ul>
     * <li>Primary code is not null or empty</li>
     * <li>Primary code exists in the billing service code table</li>
     * <li>Secondary code is not null or empty</li>
     * <li>Secondary code exists in the billing service code table</li>
     * </ul>
     * <p>
     * Validation errors are added to action errors for display to the user.
     *
     * @return boolean true if validation passes, false if any validation fails
     */
    public boolean validateForm() {
        boolean test = true;
        BillingAssociationPersistence per = new BillingAssociationPersistence();
        if (MODE_EDIT.equals(this.actionMode)) {
            if (primaryCode == null || "".equals(primaryCode)) {
                test = false;
                addActionError(getText("oscar.billing.CA.BC.billingBC.error.nullservicecode", primaryCode));
            } else if (!per.serviceCodeExists(primaryCode)) {
                test = false;
                addActionError(getText("oscar.billing.CA.BC.billingBC.error.invalidsvccode", primaryCode));
            }
            if (secondaryCode == null || "".equals(secondaryCode)) {
                test = false;
                addActionError(getText("oscar.billing.CA.BC.billingBC.error.nullservicecode", secondaryCode));
            } else if (!per.serviceCodeExists(secondaryCode)) {
                test = false;
                addActionError(getText("oscar.billing.CA.BC.billingBC.error.invalidsvccode", secondaryCode));
            }
        }
        return test;
    }

    /** Action mode constant for edit/create operation */
    public static final String MODE_EDIT = "edit";

    /** Action mode constant for delete operation */
    public static final String MODE_DELETE = "delete";

    /** Action mode constant for view/list operation (default) */
    public static final String MODE_VIEW = "view";

    /** Current operation mode, defaults to view */
    private String actionMode = MODE_VIEW;

    /** Primary billing service code for the association */
    private String primaryCode;

    /** Secondary/supplementary billing service code for the association */
    private String secondaryCode;

    /** Association ID for delete operations */
    private String id;

    public String getActionMode() {
        return actionMode;
    }

    public void setActionMode(String actionMode) {
        this.actionMode = actionMode;
    }

    public void setSecondaryCode(String secondaryCode) {
        this.secondaryCode = secondaryCode;
    }

    public void setPrimaryCode(String primaryCode) {
        this.primaryCode = primaryCode;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPrimaryCode() {
        return primaryCode;
    }

    public String getSecondaryCode() {
        return secondaryCode;
    }

    public String getId() {
        return id;
    }

}
