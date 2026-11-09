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
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.billings.ca.on.web.BillingRequestGuards;

/** Compatibility writer for typed legacy clients and the supported association editor. */
public class AssociateCodes2Action extends SaveAssoc2Action {
    private ServiceCodeAssociation legacyAssociation;

    @Override
    public String execute() {
        // Preserve the old no-diagnosis no-op, after checking the same privilege and verb as the live writer.
        if (legacyAssociation != null && !legacyAssociation.hasDXCodes()) {
            var request = ServletActionContext.getRequest();
            var response = ServletActionContext.getResponse();
            var login = LoggedInInfo.getLoggedInInfoFromSession(request);
            if (!SpringUtils.getBean(SecurityInfoManager.class).hasPrivilege(login, "_billing", "w", null)) {
                throw new SecurityException("missing required sec object (_billing)");
            }
            if (!BillingRequestGuards.requirePost(request, response)) return NONE;
            try { response.sendRedirect(request.getContextPath() + "/billing/CA/BC/showServiceCodeAssocs"); }
            catch (java.io.IOException failure) { throw new IllegalStateException("Association response failed", failure); }
            return NONE;
        }
        return super.execute();
    }

    @Override
    @StrutsParameter(depth = 1)
    public ServiceCodeAssociation getSvcAssoc() {
        return legacyAssociation == null ? super.getSvcAssoc() : legacyAssociation;
    }

    @StrutsParameter
    public void setSvcAssoc(ServiceCodeAssociation association) { legacyAssociation = association; }
}
