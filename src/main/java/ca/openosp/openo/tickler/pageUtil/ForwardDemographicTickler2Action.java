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


package ca.openosp.openo.tickler.pageUtil;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import org.owasp.encoder.Encode;
import ca.openosp.openo.demographic.data.DemographicNameAgeString;

/**
 * This class is used to forward to the add tickler screen with the demographic preselected
 *
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ForwardDemographicTickler2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Creates a new instance of ForwardDemographicTickler2Action
     */
    public ForwardDemographicTickler2Action() {
    }

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_tickler", "u", null)) {
            throw new RuntimeException("missing required sec object (_tickler)");
        }

        String demoNo = request.getParameter("demographic_no");
        String providerNo = request.getParameter("providerNo");
        
        // Ensure that both demographic number of patient and provier number are both not null
        if (demoNo != null && providerNo != null) {
            // If demographic number does not equal to providers number (only for patient tickler), get the patient name
            // Else get the providers name (only for doctor tickler)
       	  demoNo = Encode.forHtmlContent(demoNo);
            if (!demoNo.equals(providerNo)) {
                Map<String, String> h = DemographicNameAgeString.getInstance().getNameAgeSexHashtable(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo);
                request.setAttribute("demographic_no", demoNo);
                request.setAttribute("demoName", h.get("lastName") + ", " + h.get("firstName"));
            } else {
                String providerName = EDocUtil.getProviderName(providerNo);
                request.setAttribute("demographic_no", providerNo);
                request.setAttribute("demoName", providerName);
            }

            String docType = request.getParameter("docType");
            String docId = request.getParameter("docId");

	  if(docType != null) {
		  docType = Encode.forHtmlContent(docType);
	  }
	  if(docId != null) {
		  docId = Encode.forHtmlContent(docId);
	  }
          request.setAttribute("docType", docType);
          request.setAttribute("docId", docId);
        }
        return SUCCESS;
    }
}
