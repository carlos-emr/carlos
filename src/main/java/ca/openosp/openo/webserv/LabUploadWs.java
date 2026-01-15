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

package ca.openosp.openo.webserv;

import org.apache.commons.io.FileUtils;
import org.apache.cxf.annotations.GZIP;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.enumerator.LabType;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import org.springframework.stereotype.Component;
import ca.openosp.OscarProperties;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.lab.FileUploadCheck;
import ca.openosp.openo.lab.ca.all.upload.HandlerClassFactory;
import ca.openosp.openo.lab.ca.all.upload.handlers.MessageHandler;
import ca.openosp.openo.lab.ca.all.util.Utilities;

import javax.jws.WebParam;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;


@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class LabUploadWs extends AbstractWs {

    private static final Logger logger = MiscUtils.getLogger();

    public String uploadCLS(
            @WebParam(name = "file_name") String fileName,
            @WebParam(name = "contents") String contents,
            @WebParam(name = "oscar_provider_no") String oscarProviderNo
    ) {
        String returnMessage, audit;

        try {
            audit = importLab(fileName, contents, LabType.CLS, oscarProviderNo);

        } catch (Exception e) {
            logger.error(e.getMessage());
            returnMessage = "{\"success\":0,\"message\":\"" +
                    e.getMessage() + "\", \"audit\":\"\"}";
            return returnMessage;
        }

        returnMessage = "{\"success\":1,\"message\":\"\", \"audit\":\"" + audit + "\"}";
        return returnMessage;
    }

    public String uploadCML(
            @WebParam(name = "file_name") String fileName,
            @WebParam(name = "contents") String contents,
            @WebParam(name = "oscar_provider_no") String oscarProviderNo
    ) {
        String returnMessage, audit;

        try {
            audit = importLab(fileName, contents, LabType.CML, oscarProviderNo);

        } catch (Exception e) {
            logger.error(e.getMessage());
            returnMessage = "{\"success\":0,\"message\":\"" +
                    e.getMessage() + "\", \"audit\":\"\"}";
            return returnMessage;
        }

        returnMessage = "{\"success\":1,\"message\":\"\", \"audit\":\"" + audit + "\"}";
        return returnMessage;
    }

    public String uploadLifelabs(
            @WebParam(name = "file_name") String fileName,
            @WebParam(name = "contents") String contents,
            @WebParam(name = "oscar_provider_no") String oscarProviderNo
    ) {
        String returnMessage, audit;

        try {
            audit = importLab(fileName, contents, LabType.MDS, oscarProviderNo);
        } catch (Exception e) {
            logger.error(e.getMessage());
            returnMessage = "{\"success\":0,\"message\":\"" +
                    e.getMessage() + "\", \"audit\":\"\"}";
            return returnMessage;
        }

        returnMessage = "{\"success\":1,\"message\":\"\", \"audit\":\"" + audit + "\"}";
        return returnMessage;
    }

    public String uploadExcelleris(
            @WebParam(name = "file_name") String fileName,
            @WebParam(name = "contents") String contents,
            @WebParam(name = "oscar_provider_no") String oscarProviderNo
    ) {
        String returnMessage, audit;

        LabType labType = LabType.EXCELLERIS;
        /*
         * Quick and dirty work around to enable the different lab handler
         * for Exelleris lab versions used in Ontario.
         * This wont be a forever solution.
         */
        if (OscarProperties.getInstance().isOntarioBillingRegion()) {
            labType = LabType.ExcellerisON;
        }

        try {
            audit = importLab(fileName, contents, labType, oscarProviderNo);
        } catch (Exception e) {
            logger.error(e.getMessage());
            returnMessage = "{\"success\":0,\"message\":\"" +
                    e.getMessage() + "\", \"audit\":\"\"}";
            return returnMessage;
        }
        returnMessage = "{\"success\":1,\"message\":\"\", \"audit\":\"" + audit + "\"}";
        return returnMessage;
    }

    public String uploadIHA(
            @WebParam(name = "file_name") String fileName,
            @WebParam(name = "contents") String contents,
            @WebParam(name = "oscar_provider_no") String oscarProviderNo
    ) {
        String returnMessage, audit;

        try {
            audit = importLab(fileName, contents, LabType.IHAPOI, oscarProviderNo);
        } catch (Exception e) {
            logger.error(e.getMessage());
            returnMessage = "{\"success\":0,\"message\":\"" +
                    e.getMessage() + "\", \"audit\":\"\"}";
            return returnMessage;
        }

        returnMessage = "{\"success\":1,\"message\":\"\", \"audit\":\"" + audit + "\"}";
        return returnMessage;
    }

    public String uploadGammaDynacare(
            @WebParam(name = "file_name") String fileName,
            @WebParam(name = "contents") String contents,
            @WebParam(name = "oscar_provider_no") String oscarProviderNo
    ) {
        String returnMessage, audit;

        try {
            audit = importLab(fileName, contents, LabType.GDML, oscarProviderNo);
        } catch (Exception e) {
            logger.error(e.getMessage());
            returnMessage = "{\"success\":0,\"message\":\"" +
                    e.getMessage() + "\", \"audit\":\"\"}";
            return returnMessage;
        }

        returnMessage = "{\"success\":1,\"message\":\"\", \"audit\":\"" + audit + "\"}";
        return returnMessage;
    }

    public String uploadCDL(
            @WebParam(name = "file_name") String fileName,
            @WebParam(name = "contents") String contents,
            @WebParam(name = "oscar_provider_no") String oscarProviderNo
    ) {
        String returnMessage, audit;

        try {
            audit = importLab(fileName, contents, LabType.CDL, oscarProviderNo);
        } catch (Exception e) {
            logger.error(e.getMessage());
            returnMessage = "{\"success\":0,\"message\":\"" +
                    e.getMessage() + "\", \"audit\":\"\"}";
            return returnMessage;
        }

        returnMessage = "{\"success\":1,\"message\":\"\", \"audit\":\"" + audit + "\"}";
        return returnMessage;
    }

    public String uploadPDF(@WebParam(name = "file_name") String fileName,
                            @WebParam(name = "contents") byte[] contents,
                            @WebParam(name = "oscar_provider_no") String oscarProviderNo) {
        logger.error("uploadPDF called file name " + fileName + " provider " + oscarProviderNo + " contnets " + contents);
        String returnMessageHandler = "{\"success\":0,\"message\":\"\"}";

        try (ByteArrayInputStream is = new ByteArrayInputStream(contents)) {
            String filePath = Utilities.savePdfFile(is, fileName);
            HttpServletRequest request = getHttpServletRequest();
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromRequest(request);

            MessageHandler msgHandler = HandlerClassFactory.getHandler("PDFDOC");
            returnMessageHandler = msgHandler.parse(loggedInInfo, oscarProviderNo, filePath, 0, request.getRemoteAddr());
        } catch (Exception e) {
            logger.error("", e);
        }
        return returnMessageHandler;
    }

    public String uploadDocumentReference(@WebParam(name = "file_name") String fileName,
                                          @WebParam(name = "contents") byte[] contents,
                                          @WebParam(name = "oscar_provider_no") String oscarProviderNo) {
        String returnMessageHandler = "{\"success\":0,\"message\":\"\"}";
        try (ByteArrayInputStream is = new ByteArrayInputStream(contents)) {
            String filePath = Utilities.saveFile(is, fileName);
            HttpServletRequest request = getHttpServletRequest();
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromRequest(request);

            MessageHandler msgHandler = HandlerClassFactory.getHandler("FHIR_COMMUNICATION_REQUEST");
            returnMessageHandler = msgHandler.parse(loggedInInfo, oscarProviderNo, filePath, 0, request.getRemoteAddr());
        } catch (Exception e) {
            logger.error("", e);
        }
        return returnMessageHandler;
    }

    private String importLab(String fileName, String labContent, LabType labType, String oscarProviderNo)
            throws Exception {
        HttpServletRequest request = getHttpServletRequest();

        OscarProperties props = OscarProperties.getInstance();
        String labFolderPath = props.getProperty("DOCUMENT_DIR") + "labs";
        String retVal = "";

        LoggedInInfo loggedInInfo =
                LoggedInInfo.getLoggedInInfoFromRequest(request);
        String ipAddr = request.getRemoteAddr();


        // Validate filename before any processing
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("Invalid filename: filename cannot be null or empty");
        }
        
        // Check for path traversal sequences early
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename: contains path traversal characters");
        }
        
        // Canonicalize the lab folder path to establish safe base directory
        File labFolder = new File(labFolderPath).getCanonicalFile();

        if (!labFolder.exists()) {
            if (!labFolder.mkdir()) {
                throw new IOException("Failed to create lab upload folder! " + labFolderPath);
            }
        }

        // Sanitize the filename - remove any remaining dangerous characters
        // Use same naming convention as manually uploaded labs
        String sanitizedFileName = "LabUpload." + fileName.replaceAll("\\.enc$", "") + "." + (new Date()).getTime();

        // Use PathValidationUtils to validate and get safe file path
        File labFile;
        try {
            labFile = PathValidationUtils.validatePath(sanitizedFileName, labFolder);
        } catch (SecurityException e) {
            throw new SecurityException("Invalid file path: " + fileName, e);
        }

        // Save a copy of the lab locally. This is done to mimic the manual lab
        // upload process.
        FileUtils.writeStringToFile(labFile, labContent);

        // Upload lab info and hash to DB to check for duplicates
        FileInputStream is = new FileInputStream(labFile);
        int checkFileUploadedSuccessfully = FileUploadCheck.addFile(sanitizedFileName, is, oscarProviderNo);
        is.close();
        if (checkFileUploadedSuccessfully != FileUploadCheck.UNSUCCESSFUL_SAVE) {
            String labFilePath = labFile.getPath();  // Use the canonical file path
            logger.info("filePath" + labFilePath);
            logger.info("Type :" + labType.name());
            MessageHandler msgHandler = HandlerClassFactory.getHandler(labType.name());
            logger.info("MESSAGE HANDLER " + msgHandler.getClass().getName());

            // Parse and handle the lab
            if ((retVal = msgHandler.parse(
                    loggedInInfo,
                    getClass().getSimpleName(),
                    labFilePath,
                    checkFileUploadedSuccessfully,
                    ipAddr
            )) == null) {
                throw new ParseException("Failed to parse lab: " + sanitizedFileName + " of type: " + labType.name(), 0);
            }

        } else {
            throw new SQLException("Failed insert lab into DB (Likely duplicate lab): " + sanitizedFileName + " of type: " + labType.name());
        }

        // This will always contain one line, so let's just remove the newline characters
        retVal = retVal.replace("\n", "").replace("\r", "");

        LogAction.addLogSynchronous(loggedInInfo, "LabUploadWs.importLab", "fileUploadCheckId=" + String.valueOf(checkFileUploadedSuccessfully));

        return retVal;
    }
}
