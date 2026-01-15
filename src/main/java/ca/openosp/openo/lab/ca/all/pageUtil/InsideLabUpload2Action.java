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


/*
 * InsideLabUpload2Action.java
 *
 * Created on June 28, 2007, 1:11 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.lab.ca.all.pageUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.lab.FileUploadCheck;
import ca.openosp.openo.lab.ca.all.upload.HandlerClassFactory;
import ca.openosp.openo.lab.ca.all.upload.handlers.MessageHandler;
import ca.openosp.openo.lab.ca.all.util.Utilities;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class InsideLabUpload2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private enum FileStatus {
        COMPLETED,
        FAILED,
        INVALID,
        EXISTS
    }

    private List<File> importFiles;
    private List<String> importFilesFileName;
    private List<String> importFilesContentType;
    
    @Override
    public String execute() {
        if (importFiles == null || importFiles.isEmpty()) {
            addActionError("No files were uploaded");
            return INPUT;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(ServletActionContext.getRequest());
        checkUserPrivilege(loggedInInfo);

        Map<String, FileStatus> filesStatusMap = new HashMap<>();
        
        for (int i = 0; i < importFiles.size(); i++) {
            File file = importFiles.get(i);
            String fileName = importFilesFileName.get(i);
            String contentType = importFilesContentType.get(i);
            
            // Process each file
            FileStatus status = processUploadedFile(loggedInInfo, file, fileName, contentType);
            filesStatusMap.put(fileName, status);
        }
        
        ServletActionContext.getRequest().setAttribute("filesStatusMap", filesStatusMap);
        return SUCCESS;
    }

    private void checkUserPrivilege(LoggedInInfo loggedInInfo) {
        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }
    }

    private FileStatus processUploadedFile(LoggedInInfo loggedInInfo, File file, String fileName, String contentType) {
        // Convert File to InputStream and process
        try (InputStream inputStream = new FileInputStream(file)) {
            String filePath = Utilities.saveFile(inputStream, fileName);
            // Continue with your existing processing logic
            return processFile(loggedInInfo, ServletActionContext.getRequest(), filePath, getFileType(ServletActionContext.getRequest()));
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error processing file: " + fileName, e);
            return FileStatus.FAILED;
        }
    }

    private String getFileType(HttpServletRequest request) {
        String fileType = request.getParameter("type");
        String otherFileType = request.getParameter("otherType");
        if (fileType != null && !fileType.equals("OTHER")) {
            return fileType;
        }
        if (otherFileType != null) {
            return otherFileType;
        }

        return null;
    }

    private FileStatus processFile(LoggedInInfo loggedInInfo, HttpServletRequest request, String filePath, String fileType) {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        int checkFileUploadedSuccessfully;

        try (InputStream localFileInputStream = Files.newInputStream(path)) {
            String providerNumber = (String) request.getSession().getAttribute("user");
            checkFileUploadedSuccessfully = FileUploadCheck.addFile(fileName, localFileInputStream, providerNumber);
            if (checkFileUploadedSuccessfully == FileUploadCheck.UNSUCCESSFUL_SAVE) {
                return FileStatus.EXISTS;
            }
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error occurred while processing " + fileName + " file", e);
            return FileStatus.FAILED;
        }

        MessageHandler msgHandler = HandlerClassFactory.getHandler(fileType);
        if ((msgHandler.parse(loggedInInfo, getClass().getSimpleName(), filePath, checkFileUploadedSuccessfully, request.getRemoteAddr())) != null) {
            return FileStatus.COMPLETED;
        }
        return FileStatus.INVALID;
    }

    public List<File> getImportFiles() 
    { 
        return importFiles; 
    }
    public void setImportFiles(List<File> importFiles) 
    { 
        this.importFiles = importFiles; 
    }
    
    public List<String> getImportFilesFileName() 
    { 
        return importFilesFileName; 
    }
    public void setImportFilesFileName(List<String> importFilesFileName) 
    { 
        this.importFilesFileName = importFilesFileName; 
    }
    
    public List<String> getImportFilesContentType() 
    { 
        return importFilesContentType; 
    }
    public void setImportFilesContentType(List<String> importFilesContentType) 
    { 
        this.importFilesContentType = importFilesContentType; 
    }
}
