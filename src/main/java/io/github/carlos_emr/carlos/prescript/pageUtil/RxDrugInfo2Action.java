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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.IOException;
import java.net.URLEncoder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prescript.data.RxDrugData;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action that redirects to external drug information resources.
 * <p>
 * Looks up drug information by generic name (GN) or brand name (BN) and redirects
 * the user to the OSCAR resource search page for detailed drug monograph information.
 * When a brand name is provided, the generic name is resolved via DrugRef before redirect.
 * Requires {@code _rx} read privilege.
 *
 * @since 2026-03-17
 */
public final class RxDrugInfo2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public String execute()
            throws IOException, ServletException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "r", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }


        String GN = null;
        String BN = null;

        if (request.getParameter("GN") != null) {
            if (!request.getParameter("GN").equals("null")) {
                GN = request.getParameter("GN");
                response.sendRedirect("http://resource.oscarmcmaster.org/oscarResource/OSCAR_search/OSCAR_search_results?title=" + URLEncoder.encode(GN, "UTF-8"));
            }
        }

        if (request.getParameter("BN") != null) {
            if (!request.getParameter("BN").equals("null")) {
                BN = request.getParameter("BN");
                RxDrugData drugData = new RxDrugData();
                String genName = null;
                try {
                    genName = drugData.getGenericName(BN);
                } catch (Exception e) {
                    genName = BN;
                }
                response.sendRedirect("http://resource.oscarmcmaster.org/oscarResource/OSCAR_search/OSCAR_search_results?title=" + URLEncoder.encode(genName, "UTF-8"));
            }
        }

        if (GN == null && BN == null) {
            //Need to return to just the search page on oscarResource
        }

        return null;
    }
}
