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

package io.github.carlos_emr.carlos.hospitalReportManager;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.ConcatPDF;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class PrintHRMReport2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)) {
            throw new SecurityException("missing required security object: _hrm");
        }

        DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
        HRMDocumentToDemographicDao hrmDocumentToDemographicDao = SpringUtils.getBean(HRMDocumentToDemographicDao.class);

        List<Object> pdfDocs = new ArrayList<Object>();
        List<TempPdfFile> tempFiles = new ArrayList<>();
        String[] hrmReportIds = request.getParameterValues("hrmReportId");
        List<Integer> hrmIds = new ArrayList<>();

        try {
            if (hrmReportIds != null) {
                for (String hrmReportId : hrmReportIds) {
                    try {
                        hrmIds.add(Integer.valueOf(hrmReportId));
                    } catch (NumberFormatException e) {
                        logger.warn("Rejected HRM PDF request with invalid hrmReportId={}",
                                LogSafe.sanitize(hrmReportId));
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid HRM report id");
                        return NONE;
                    }
                }
            }
            if (hrmIds.isEmpty()) {
                logger.warn("Rejected HRM PDF request without hrmReportId values");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing HRM report id");
                return NONE;
            }

            response.setContentType("application/pdf");  //octet-stream
            response.setHeader("Content-Disposition", "attachment; filename=\"HRMReport_"
                    + System.currentTimeMillis() + ".pdf\"");
            Path docDir = validatedDocumentDirectory(
                    CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"));

            for (Integer hrmId : hrmIds) {
                Demographic demographic = null;
                List<HRMDocumentToDemographic> demographicHrms = hrmDocumentToDemographicDao.findByHrmDocumentId(hrmId);
                if (demographicHrms != null && !demographicHrms.isEmpty() && demographicHrms.get(0).getDemographicNo() != null) {
                    int demographicNo = demographicHrms.get(0).getDemographicNo();
                    demographic = demographicDao.getDemographicById(demographicNo);
                }

                if (demographic == null) {
                    logger.info("HRM PDF request has no demographic mapping for hrmId={}", hrmId);
                }

                // hrmId is integer-parsed above; no user-controlled path segment reaches the temp filename.
                File tempFile = Files.createTempFile(docDir, "hrm-report-" + hrmId + "-", ".pdf").toFile();
                pdfDocs.add(tempFile.getPath());
                tempFiles.add(new TempPdfFile(tempFile, hrmId));

                try (FileOutputStream osTemp = new FileOutputStream(tempFile)) {
                    HRMPDFCreator hrmpdfCreator = new HRMPDFCreator(osTemp, hrmId, loggedInInfo);
                    hrmpdfCreator.printPdf();
                    osTemp.flush(); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- binary PDF temp stream
                }
            }
            ConcatPDF.concat(pdfDocs, response.getOutputStream());

        } catch (SecurityException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            if (response.isCommitted()) {
                // Once PDF bytes are committed, a follow-up error page would corrupt the stream;
                // log the partial-response condition and let the direct-response action end.
                logger.error("Could not generate or stream HRM PDF response after response commit; "
                        + "client may receive headers and zero or more partial PDF bytes: "
                        + "responseCommitted=true", e);
                return NONE;
            }
            logger.error("Could not generate or stream HRM PDF response before commit", e);
            try {
                response.resetBuffer();
                response.setContentType("text/html;charset=UTF-8");
                response.setHeader("Content-Disposition", "inline");
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate HRM PDF");
            } catch (IOException sendErrorException) {
                logger.error("Could not send HRM PDF error response", sendErrorException);
            }
            return NONE;
        } finally {
            for (TempPdfFile tempPdfFile : tempFiles) {
                File tempFile = tempPdfFile.file();
                if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                    logger.warn("Could not delete temporary HRM PDF file for hrmId={}",
                            tempPdfFile.hrmId());
                }
            }
        }


        // The success path streams the PDF directly; there is no success result mapping.
        // Returning SUCCESS makes Struts render the global error page over the PDF as "0".
        return NONE;
    }

    /**
     * Resolves and validates the configured HRM document directory before PDF temp files are
     * created.
     *
     * <p>This is a filesystem safety gate for direct-response PDF generation. Callers must fail
     * before streaming when the configured path is blank, syntactically invalid, or does not point
     * to an existing directory.</p>
     *
     * @param configuredDocumentDir raw {@code DOCUMENT_DIR} {@link CarlosProperties} configuration value
     * @return normalized absolute document directory
     * @throws IOException when the value is blank, invalid, or not an existing directory
     */
    static Path validatedDocumentDirectory(String configuredDocumentDir) throws IOException {
        if (configuredDocumentDir == null || configuredDocumentDir.trim().isEmpty()) {
            throw new IOException("DOCUMENT_DIR is not configured for HRM PDF generation");
        }

        Path documentDirectory;
        try {
            documentDirectory = Path.of(configuredDocumentDir).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IOException("DOCUMENT_DIR is not a valid path for HRM PDF generation", e);
        }

        if (!Files.isDirectory(documentDirectory)) {
            throw new IOException("DOCUMENT_DIR is not an existing directory for HRM PDF generation");
        }
        return documentDirectory;
    }

    private record TempPdfFile(File file, Integer hrmId) {
    }
}
