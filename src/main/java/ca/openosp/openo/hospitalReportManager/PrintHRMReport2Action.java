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

package ca.openosp.openo.hospitalReportManager;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentToDemographic;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.util.ConcatPDF;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class PrintHRMReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        int demographicNo = 0;
        Demographic demographic = null;
        DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
        HRMDocumentToDemographicDao hrmDocumentToDemographicDao = SpringUtils.getBean(HRMDocumentToDemographicDao.class);

        List<Object> pdfDocs = new ArrayList<Object>();
        File fileTemp = null;
        FileOutputStream osTemp = null;
        String[] hrmReportIds = request.getParameterValues("hrmReportId");
        List<Integer> hrmIds = new ArrayList<>();

        try {
            if (request.getParameterValues("hrmReportId") != null) {
                for (int i = 0; i < hrmReportIds.length; i++) {
                    try {
                        hrmIds.add(Integer.valueOf(hrmReportIds[i]));
                    } catch (NumberFormatException e) {
                        logger.error("Could not parse " + hrmReportIds[i] + " to an integer.");
                    }
                }
            }

            String fileName = "";
            response.setContentType("application/pdf");  //octet-stream

            for (Integer hrmId : hrmIds) {
                List<HRMDocumentToDemographic> demographicHrms = hrmDocumentToDemographicDao.findByHrmDocumentId(hrmId);
                if (demographicHrms != null && !demographicHrms.isEmpty() && demographicHrms.get(0).getDemographicNo() != null) {
                    demographicNo = demographicHrms.get(0).getDemographicNo();
                    demographic = demographicDao.getDemographicById(demographicNo);
                }

                String fileTempName = "";
                if (demographic != null) {
                    fileTempName = OscarProperties.getInstance().getProperty("DOCUMENT_DIR") + "//" + demographic.getLastName() + "_" + demographic.getFirstName() + "_" + hrmId + "_HRMReport.pdf";
                    fileName = demographic.getLastName() + "_" + demographic.getFirstName() + "_HRMReport" + "_" + (new Date().getTime()) + ".pdf";
                } else {
                    fileTempName = OscarProperties.getInstance().getProperty("DOCUMENT_DIR") + "//HRMReport.pdf";
                    fileName = "_HRMReport" + "_" + (new Date().getTime()) + ".pdf";
                }
                response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

                // Create temporary file
                pdfDocs.add(fileTempName);
                fileTemp = new File(fileTempName);
                osTemp = new FileOutputStream(fileTemp);

                HRMPDFCreator hrmpdfCreator = new HRMPDFCreator(osTemp, hrmId, loggedInInfo);
                hrmpdfCreator.printPdf();
            }
            ConcatPDF.concat(pdfDocs, response.getOutputStream());

        } catch (IOException e) {
            logger.error("Could not retrieve the Output Stream from the response", e);
            request.setAttribute("printError", true);
            return "error";
        } finally {
            if (osTemp != null) {
                osTemp.flush();
                osTemp.close();
            }
            if (fileTemp != null) {
                fileTemp.delete();
            }
        }


        return SUCCESS;
    }
}
