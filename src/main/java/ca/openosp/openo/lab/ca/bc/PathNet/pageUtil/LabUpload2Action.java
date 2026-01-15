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


package ca.openosp.openo.lab.ca.bc.PathNet.pageUtil;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.lab.FileUploadCheck;
import ca.openosp.openo.lab.ca.bc.PathNet.Connection;
import ca.openosp.openo.lab.ca.bc.PathNet.HL7.Message;
import ca.openosp.openo.utility.PathValidationUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class LabUpload2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    Logger _logger = MiscUtils.getLogger();

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String filename = "";
        String proNo = (String) request.getSession().getAttribute("user");
        String outcome = "";

        try {
            // Validate the uploaded file to prevent path traversal attacks
            if (importFile == null) {
                _logger.error("No file provided for upload");
                outcome = "exception";
                request.setAttribute("outcome", outcome);
                return SUCCESS;
            }

            // Validate file is from an allowed temp directory
            try {
                importFile = PathValidationUtils.validateUpload(importFile);
            } catch (SecurityException e) {
                _logger.error("Invalid upload source - potential path traversal: " + importFile.getPath());
                outcome = "exception";
                request.setAttribute("outcome", outcome);
                return SUCCESS;
            }

            MiscUtils.getLogger().debug("Lab Upload content type = " + importFile.getName());
            // Re-validate at point of use for static analysis visibility
            File validatedImportFile = PathValidationUtils.validateUpload(importFile);
            InputStream is = Files.newInputStream(validatedImportFile.toPath());
            filename = importFile.getName();

            int check = FileUploadCheck.addFile(filename, is, proNo);
            is.reset();
            if (check != FileUploadCheck.UNSUCCESSFUL_SAVE) {
                Connection connection = new Connection();
                ArrayList<String> messages = connection.Retrieve(is);
                if (messages != null) {
                    try {
                        int size = messages.size();

                        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                        for (int i = 0; i < size; i++) {
                            if (_logger.isDebugEnabled()) {
                                _logger.debug("Call Message Constructor for message # " + i);
                            }
                            Message message = new Message(now);
                            if (_logger.isDebugEnabled()) {
                                _logger.debug("Call Message.Parse for message # " + i);
                            }
                            message.Parse(messages.get(i));
                            if (_logger.isDebugEnabled()) {
                                _logger.debug("Call Message.ToDatabase for message # " + i);
                            }
                            message.ToDatabase();
                        }
                        outcome = "success";
                    } catch (Exception ex) {
                        //success = false; //<- for future when transactional
                        _logger.error("Error - oscar.PathNet.Contorller - Message: " + ex.getMessage() + " = " + ex.toString(), ex);
                        outcome = "exception";
                    }
                    //connection.Acknowledge(success);
                }
                //SAVE FILE TO DISK
                is.reset();
                saveFile(is, filename);
            } else {
                outcome = "uploadedPreviously";
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            outcome = "exception";
        }
        request.setAttribute("outcome", outcome);
        return SUCCESS;
    }


    public LabUpload2Action() {
    }


    /**
     * Save a Jakarta FormFile to a preconfigured place.
     *
     * @param stream
     * @param filename
     * @return boolean
     */
    private static boolean saveFile(InputStream stream, String filename) {
        String retVal = null;
        boolean isAdded = true;

        try {
            //retrieve the file data
            // ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //InputStream stream = file.getInputStream();
            OscarProperties props = OscarProperties.getInstance();

            //properties must exist
            String place = props.getProperty("DOCUMENT_DIR");

            if (!place.endsWith("/"))
                place = new StringBuilder(place).insert(place.length(), "/").toString();
            retVal = place + "LabUpload." + filename + "." + (new Date()).getTime();
            MiscUtils.getLogger().debug(retVal);
            //write the file to the file specified
            OutputStream bos = new FileOutputStream(retVal);
            int bytesRead = 0;
            //byte[] buffer = file.getFileData();
            //while ((bytesRead = stream.read(buffer)) != -1){
            //   bos.write(buffer, 0, bytesRead);
            while ((bytesRead = stream.read()) != -1) {
                bos.write(bytesRead);
            }
            bos.close();

            //close the stream
            stream.close();
        } catch (FileNotFoundException fnfe) {

            MiscUtils.getLogger().debug("File not found");
            MiscUtils.getLogger().error("Error", fnfe);
            return isAdded = false;

        } catch (IOException ioe) {
            MiscUtils.getLogger().error("Error", ioe);
            return isAdded = false;
        }

        return isAdded;
    }

    private File importFile;

    public File getImportFile() {
        return importFile;
    }

    public void setImportFile(File importFile) {
        this.importFile = importFile;
    }
}
