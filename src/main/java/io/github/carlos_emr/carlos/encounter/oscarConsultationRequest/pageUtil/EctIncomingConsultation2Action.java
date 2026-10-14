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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * The schedule banner's Consultations entry point ({@code encounter/IncomingConsultation}).
 *
 * <p>Forwards to the same {@code ViewConsultationRequests.jsp} as {@code encounter/ViewConsultation},
 * so it publishes the Consultant/Provider filter attributes (issue #3976) through
 * {@link ConsultationListFilterResolver} too; without them the Provider dropdown would be empty
 * on the page a user reaches first. The banner link carries no filter, so none is applied here.</p>
 */
public class EctIncomingConsultation2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final ConsultationListFilterResolver filterResolver;

    /**
     * Struts/Spring entry point: resolves the collaborators from the Spring context.
     */
    public EctIncomingConsultation2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), ConsultationListFilterResolver.fromSpringContext());
    }

    /**
     * Test constructor.
     */
    EctIncomingConsultation2Action(SecurityInfoManager securityInfoManager,
                                   ConsultationListFilterResolver filterResolver) {
        this.securityInfoManager = securityInfoManager;
        this.filterResolver = filterResolver;
    }

    public String execute()
            throws ServletException, IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "w", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        filterResolver.publish(request, loggedInInfo, null, null);
        return SUCCESS;
    }


    public String getProviderNo() {
        if (providerNo == null)
            providerNo = new String();
        return providerNo;
    }

    @StrutsParameter
    public void setProviderNo(String str) {
        providerNo = str;
    }

    public String getDemographicNo() {
        if (demographicNo == null)
            demographicNo = new String();
        return demographicNo;
    }

    @StrutsParameter
    public void setDemographicNo(String str) {
        demographicNo = str;
    }

    String providerNo;
    String demographicNo;
}
