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
package io.github.carlos_emr.carlos.commn.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.OscarDocumentCreator;
import io.github.carlos_emr.carlos.util.ConcatPDF;

/**
 * Originally developed by Prylynx for SJHCG
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class PrintReferralLabel2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public PrintReferralLabel2Action() {
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    @SuppressWarnings("resource")
    private InputStream getInputStream() {
        InputStream ins = null;
        try {
            ins = new FileInputStream(PathValidationUtils.resolveTrustedPath(new File(System.getProperty("user.home") + "/reflabel.xml")));
        } catch (IOException | SecurityException e) {
            MiscUtils.getLogger().warn("no reflabel.xml found in user's home directory, going to backup", e);
        }
        if (ins == null) {
            ins = getClass().getResourceAsStream("/org/oscarehr/common/web/reflabel.xml");
        }
        return ins;
    }

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        //patient
        String classpath = (String) request.getSession().getServletContext().getAttribute("org.apache.catalina.jsp_classpath");
        System.setProperty("jasper.reports.compile.class.path", classpath);

        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("billingreferral_no", request.getParameter("billingreferralNo"));
        ServletOutputStream sos = null;
        InputStream ins = null;

        try {
            sos = response.getOutputStream();

            response.setHeader("Content-disposition", getHeader(response).toString());

            String idList = request.getParameter("ids");

            if ("true".equals(request.getParameter("useCheckList"))) {
                idList = "";
                List<ProfessionalSpecialist> checkedSpecs = (List<ProfessionalSpecialist>) request.getSession().getAttribute("billingReferralAdminCheckList");
                if (checkedSpecs != null && checkedSpecs.size() > 0) {
                    for (ProfessionalSpecialist ps : checkedSpecs) {
                        idList += ("," + ps.getId());
                    }
                    idList = idList.substring(1);
                }
            }
            if (!StringUtils.isEmpty(idList)) {
                String[] ids = idList.split(",");
                ArrayList<Object> printList = new ArrayList<Object>();
                OscarDocumentCreator osc = new OscarDocumentCreator();

                for (int x = 0; x < ids.length; x++) {
                    FileOutputStream fos = null;
                    try {
                        File f = File.createTempFile("physlabel", ".pdf");
                        fos = new FileOutputStream(f);
                        ins = getInputStream();
                        parameters.put("billingreferral_no", ids[x]);
                        try (Connection connection = LegacyJdbcQuery.getConnection()) {
                            osc.fillDocumentStream(parameters, fos, "pdf", ins, connection);
                        }
                        printList.add(f.getAbsolutePath());
                    } finally {
                        IOUtils.closeQuietly(fos);
                        IOUtils.closeQuietly(ins);
                    }
                }
                ConcatPDF.concat(printList, sos);
            } else {
                ins = getInputStream();
                OscarDocumentCreator osc = new OscarDocumentCreator();
                try (Connection connection = LegacyJdbcQuery.getConnection()) {
                    osc.fillDocumentStream(parameters, sos, "pdf", ins, connection);
                }
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            IOUtils.closeQuietly(ins);
        }

        if ("true".equals(request.getParameter("useCheckList"))) {
            // nosemgrep: tainted-session-from-http-request -- value is a new empty ArrayList literal, not user input
            request.getSession().setAttribute("billingReferralAdminCheckList", new ArrayList<ProfessionalSpecialist>());
        }
        // This action streams the PDF directly and its Struts mapping has no success result.
        // Returning SUCCESS makes Struts render the global error page over the PDF as "0".
        return NONE;
    }

    private StringBuilder getHeader(HttpServletResponse response) {
        StringBuilder strHeader = new StringBuilder();
        strHeader.append("label_");
        strHeader.append(".pdf");
        response.setHeader("Cache-Control", "max-age=0");
        response.setDateHeader("Expires", 0);
        response.setContentType("application/pdf");
        StringBuilder sbContentDispValue = new StringBuilder();
        sbContentDispValue.append("inline; filename="); //inline - display
        sbContentDispValue.append(strHeader);
        return sbContentDispValue;
    }

}
