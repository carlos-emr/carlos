//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
package ca.openosp.openo.olis;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.PatientLabRoutingDao;
import ca.openosp.openo.commn.model.PatientLabRouting;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.lab.FileUploadCheck;
import ca.openosp.openo.lab.ca.all.upload.HandlerClassFactory;
import ca.openosp.openo.lab.ca.all.upload.handlers.OLISHL7Handler;
import ca.openosp.openo.lab.ca.on.CommonLabResultData;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class OLISAddToInbox2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    // UUID validation pattern to prevent path injection
    private static final Pattern UUID_PATTERN = Pattern.compile("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$");

    static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String uuidToAdd = request.getParameter("uuid");
        String pFile = request.getParameter("file");
        String pAck = request.getParameter("ack");
        boolean doFile = false, doAck = false;
        if (pFile != null && pFile.equals("true")) {
            doFile = true;
        }
        if (pAck != null && pAck.equals("true")) {
            doAck = true;
        }

        // Validate UUID to prevent path injection attacks
        if (uuidToAdd == null || !UUID_PATTERN.matcher(uuidToAdd).matches()) {
            logger.error("Invalid UUID provided: " + uuidToAdd);
            request.setAttribute("result", "Error");
            return "ajax";
        }

        // Use secure file path construction with PathValidationUtils
        String tmpDir = System.getProperty("java.io.tmpdir");
        String fileName = "olis_" + uuidToAdd + ".response";
        File tempDirectory = new File(tmpDir);
        File file;
        String fileLocation;
        try {
            file = PathValidationUtils.validatePath(fileName, tempDirectory);
            fileLocation = file.getCanonicalPath();
        } catch (SecurityException e) {
            logger.error("Attempted path traversal detected for UUID: " + uuidToAdd);
            request.setAttribute("result", "Error");
            return "ajax";
        } catch (IOException e) {
            logger.error("Error validating file path for UUID: " + uuidToAdd, e);
            request.setAttribute("result", "Error");
            return "ajax";
        }
        
        OLISHL7Handler msgHandler = (OLISHL7Handler) HandlerClassFactory.getHandler("OLIS_HL7");

        InputStream is = null;
        try {
            is = new FileInputStream(file);
            int check = FileUploadCheck.addFile(file.getName(), is, providerNo);

            if (check != FileUploadCheck.UNSUCCESSFUL_SAVE) {
                if (msgHandler.parse(loggedInInfo, "OLIS_HL7", fileLocation, check, true) != null) {
                    request.setAttribute("result", "Success");
                    if (doFile) {
                        ArrayList<String[]> labsToFile = new ArrayList<String[]>();
                        String item[] = new String[]{String.valueOf(msgHandler.getLastSegmentId()), "HL7"};
                        labsToFile.add(item);
                        CommonLabResultData.fileLabs(labsToFile, providerNo);
                    }
                    if (doAck) {
                        String demographicID = getDemographicIdFromLab("HL7", msgHandler.getLastSegmentId());
                        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ACK, LogConst.CON_HL7_LAB, "" + msgHandler.getLastSegmentId(), request.getRemoteAddr(), demographicID);
                        CommonLabResultData.updateReportStatus(msgHandler.getLastSegmentId(), providerNo, 'A', "comment", "HL7");

                    }
                } else {
                    request.setAttribute("result", "Error");
                }
            } else {
                request.setAttribute("result", "Already Added");
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't add requested OLIS lab to Inbox.", e);
            request.setAttribute("result", "Error");
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                //ignore
            }
        }

        return "ajax";
    }

    private static String getDemographicIdFromLab(String labType, int labNo) {
        PatientLabRoutingDao dao = SpringUtils.getBean(PatientLabRoutingDao.class);
        PatientLabRouting routing = dao.findDemographics(labType, labNo);
        return routing == null ? "" : String.valueOf(routing.getDemographicNo());
    }
}
