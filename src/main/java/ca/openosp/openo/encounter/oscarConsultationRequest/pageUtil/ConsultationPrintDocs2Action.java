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


package ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.util.ConcatPDF;
import ca.openosp.openo.util.UtilDateUtilities;

/**
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ConsultationPrintDocs2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        String reqId = request.getParameter("reqId");
        String demoNo = request.getParameter("demographicNo");
        //ArrayList docs = EDocUtil.listDocs( demoNo, reqId, EDocUtil.ATTACHED);        
        String[] docs = request.getParameterValues("docNo");
        ArrayList alist = new ArrayList();
        String path = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");

        for (int i = 0; i < docs.length; i++)
            alist.add(path + docs[i]);


        if (alist.size() > 0) {
            response.setContentType("application/pdf");  //octet-stream
            response.setHeader("Content-Disposition", "attachment; filename=\"combinedPDF-" + UtilDateUtilities.getToday("yyyy-mm-dd.hh.mm.ss") + ".pdf\"");
            try {
                ConcatPDF.concat(alist, response.getOutputStream());
            } catch (IOException ex) {
                MiscUtils.getLogger().error("Error", ex);
            }
            return null;
        }

        return "noprint";
    }

    /**
     * Creates a new instance of ConsultationPrintDocs2Action
     */
    public ConsultationPrintDocs2Action() {
    }

}
