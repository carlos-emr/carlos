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
import javax.servlet.http.HttpServletResponse;


/**
 * <p>Title: AssociateCodes2Action</p>
 *
 * <p>Description:This Action is responsible for associating a service code with up to three use specified Diagnostic codes</p>
 *
 * <p>Copyright: Copyright (c) 2005</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.IOException;

public class AssociateCodes2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {
        ServiceCodeAssociation svc = this.getSvcAssoc();
        /**
         * Send back to originating screen if there are no associated codes selected
         *
         */
        if (!svc.hasDXCodes()) {
            try {
                response.sendRedirect(request.getContextPath() + "/billing/CA/BC/dxcode_svccode_assoc.jsp");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return NONE;
        }
        BillingAssociationPersistence per = new BillingAssociationPersistence();
        ServiceCodeAssociation assoc = this.getSvcAssoc();
        per.saveServiceCodeAssociation(assoc, this.getMode());
        return SUCCESS;
    }

    private ServiceCodeAssociation svcAssoc;
    private String mode;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public ServiceCodeAssociation getSvcAssoc() {
        return svcAssoc;
    }

    public void setSvcAssoc(ServiceCodeAssociation svcAssoc) {
        this.svcAssoc = svcAssoc;
    }
}
