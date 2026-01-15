/**
 * Copyright (c) 2014-2015. KAI Innovations Inc. All Rights Reserved.
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
package ca.openosp.openo.integration.mcedt.mailbox;

import ca.ontario.health.edt.*;
import org.apache.struts2.ActionSupport;
import org.apache.cxf.helpers.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.integration.mcedt.DelegateFactory;
import ca.openosp.openo.integration.mcedt.McedtMessageCreator;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.OscarProperties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class Upload2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("cancelUpload".equals(method)) {
            return cancelUpload();
        } else if ("addNew".equals(method)) {
            return addNew();
        } else if ("removeSelected".equals(method)) {
            return removeSelected();
        } else if ("uploadToMcedt".equals(method)) {
            return uploadToMcedt();
        } else if ("submitToMcedt".equals(method)) {
            return submitToMcedt();
        } else if ("uploadSubmitToMcedt".equals(method)) {
            return uploadSubmitToMcedt();
        } else if ("deleteUpload".equals(method)) {
            return deleteUpload();
        } else if ("addUpload".equals(method)) {
            return addUpload();
        }

        ActionUtils.removeSuccessfulUploads(request);
        ActionUtils.removeUploadResponseResults(request);
        ActionUtils.removeSubmitResponseResults(request);
        Date startDate = ActionUtils.getOutboxTimestamp();
        Date endDate = new Date();
        if (startDate != null && endDate != null) {
            ActionUtils.moveOhipToOutBox(startDate, endDate);
            
            /*
             * The method ActionUtils.moveObecToOutBox is slow with many files in 'OscarDocument/oscar/document/'.
             * To optimize, we will move OBEC files during generation rather than during MCEDT mailbox opening.
             * See ObecData.writeFile() for details on the updated process.
             */
            // ActionUtils.moveObecToOutBox(startDate,endDate);	

            ActionUtils.setOutboxTimestamp(endDate);
        }
        ActionUtils.setUploadResourceId(request, new BigInteger("-1"));

        return SUCCESS;
    }

    public String cancelUpload() throws Exception {
        ActionUtils.removeUploadResourceId(request);
        ActionUtils.removeUploadFileName(request);
        List<File> files = ActionUtils.getSuccessfulUploads(request);
        OscarProperties props = OscarProperties.getInstance();
        File sent = new File(props.getProperty("ONEDT_SENT", ""));
        if (!sent.exists())
            FileUtils.mkDir(sent);

        try {
            if (files != null && files.size() > 0) {
                for (File file : files) {
                    ActionUtils.moveFileToDirectory(file, sent, false, true);
                }
            }
        } catch (IOException e) {
            logger.error("A exception has occured while moving files at " + new Date());

            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("uploadAction.upload.faultException", new String[]{errorMessage}));
            return "failure";
        }
        ActionUtils.removeSuccessfulUploads(request);
        ActionUtils.removeUploadResponseResults(request);
        ActionUtils.removeSubmitResponseResults(request);

        return "cancel";
    }

    public String addNew() throws Exception {
        return "addNew";
    }

    public String removeSelected() throws Exception {
        return SUCCESS;
    }

    public String uploadToMcedt() {
        if (this.getResourceId().equals(new BigInteger("-1"))) {
            List<UploadData> uploads = new ArrayList<UploadData>();
            uploads.add(toUpload());

            try {
                EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(this.getDescription()));
                ResourceResult result;

                try {
                    result = delegate.upload(uploads);
                } catch (Faultexception e) {
                    logger.error("A fault exception has occured while auto uploading MCEDT files at " + new Date());

                    String errorMessage = McedtMessageCreator.exceptionToString(e);
                    addActionError(getText("uploadAction.upload.failure", new String[]{errorMessage}));
                    return "failure";
                }

                if (result.getResponse().get(0).getResult().getCode().equals("IEDTS0001")) {
                    ActionUtils.setUploadResourceId(request, result.getResponse().get(0).getResourceID());
                    OscarProperties props = OscarProperties.getInstance();
                    File file = new File(props.getProperty("ONEDT_OUTBOX", "") + this.getFileName());
                    ActionUtils.setSuccessfulUploads(request, file);
                } else {
                    ActionUtils.setUploadResourceId(request, new BigInteger("-2"));
                    result.getResponse().get(0).setDescription(this.getFileName()); //this is done because error response has null description
                    ActionUtils.setSubmitResponseResults(request, result.getResponse().get(0));// if upload fails, submission is also assumed failed

                }
                ActionUtils.setUploadedFileName(request, this.getFileName());
                ActionUtils.setUploadResponseResults(request, result.getResponse().get(0));

                return SUCCESS;

            } catch (Exception e) {
                logger.error("Unable to upload to MCEDT", e);

                String errorMessage = McedtMessageCreator.exceptionToString(e);
                addActionError(getText("uploadAction.upload.failure", new String[]{errorMessage}));
                return "failure";
            }

        }
        return SUCCESS;
    }

    public String submitToMcedt() {
        if (!this.getResourceId().equals(new BigInteger("-2"))) {
            List<BigInteger> ids = new ArrayList<BigInteger>();
            ids.add(this.getResourceId());
            try {
                EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(this.getFileName()));
                ResourceResult result;

                try {
                    result = delegate.submit(ids);
                } catch (Faultexception e) {
                    logger.error("A fault exception has occured while auto submitting MCEDT files at " + new Date());

                    String errorMessage = McedtMessageCreator.exceptionToString(e);
                    addActionError(getText("uploadAction.submit.failure", new String[]{errorMessage}));
                    return "failure";
                }

                if (!result.getResponse().get(0).getResult().getCode().equals("IEDTS0001")) {
                    result.getResponse().get(0).setDescription(this.getFileName());
                }
                ActionUtils.setSubmitResponseResults(request, result.getResponse().get(0));
                ActionUtils.setUploadResourceId(request, new BigInteger("-1"));
                return SUCCESS;
            } catch (Exception e) {
                logger.error("Unable to submit", e);

                String errorMessage = McedtMessageCreator.exceptionToString(e);
                addActionError(getText("uploadAction.submit.failure", new String[]{errorMessage}));
                return "failure";
            }

        } else {//if file has failed at upload level, no need to try submit
            ActionUtils.setUploadResourceId(request, new BigInteger("-1"));
            return SUCCESS;
        }
    }

    public String uploadSubmitToMcedt() {
        try {
            List<String> successUploads = new ArrayList<String>();
            List<String> failUploads = new ArrayList<String>();
            List<String> successSubmits = new ArrayList<String>();
            List<String> failSubmits = new ArrayList<String>();
            List<UploadData> uploads = toUploadMultipe();
            for (UploadData upload : uploads) {
                List<UploadData> uploadData = new ArrayList<UploadData>();
                uploadData.add(upload);
                EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(ActionUtils.getServiceId(upload.getDescription()));
                ResourceResult result;

                try {
                    result = delegate.upload(uploadData);
                } catch (Faultexception e) {
                    logger.error("A fault exception has occured while manually uploading MCEDT files at " + new Date());

                    String errorMessage = McedtMessageCreator.exceptionToString(e);
                    addActionError(getText("uploadAction.submit.failure", new String[]{errorMessage}));
                    return "failure";
                }

                List<BigInteger> ids = new ArrayList<BigInteger>();
                OscarProperties props = OscarProperties.getInstance();
                File sent = new File(props.getProperty("ONEDT_SENT", ""));
                if (!sent.exists())
                    FileUtils.mkDir(sent);
                for (ResponseResult edtResponse : result.getResponse()) {
                    if (edtResponse.getResult().getCode().equals("IEDTS0001")) {
                        ids.add(edtResponse.getResourceID());
                        File file = new File(props.getProperty("ONEDT_OUTBOX", "") + edtResponse.getDescription());
                        ActionUtils.moveFileToDirectory(file, sent, false, true);
                        successUploads.add(McedtMessageCreator.resourceResultToString(result));
                    } else {
                        edtResponse.setDescription(upload.getDescription());
                        failUploads.add(edtResponse.getDescription() + ": " + edtResponse.getResult().getMsg());
                    }
                }
                if (ids.size() > 0) {

                    try {
                        result = delegate.submit(ids);
                    } catch (Faultexception e) {
                        logger.error("A fault exception has occured while manually submitting MCEDT files at " + new Date());

                        String errorMessage = McedtMessageCreator.exceptionToString(e);
                        addActionError(getText("uploadAction.submit.failure", new String[]{errorMessage}));
                        return "failure";
                    }

                    for (ResponseResult edtResponse : result.getResponse()) {
                        if (edtResponse.getResult().getCode().equals("IEDTS0001")) {
                            successSubmits.add(McedtMessageCreator.resourceResultToString(result));
                        } else {
                            edtResponse.setDescription(upload.getDescription());
                            failSubmits.add(edtResponse.getDescription() + ": " + edtResponse.getResult().getMsg());
                        }
                    }
                }
            }
            // Finally save all the messages/errors
            // we don't need to find out if upload is successful, we rather get info about submit status of that file
            //if ( successUploads!=null && successUploads.size()>0 ) messages = ActionUtils.addMoreMessage(messages, "uploadAction.upload.success", McedtMessageCreator.stringListToString(successUploads));
            if (successSubmits != null && successSubmits.size() > 0) {
                addActionMessage(getText("uploadAction.submit.success", new String[]{McedtMessageCreator.stringListToString(successSubmits)}));
            }

            String key = "";
            String val = "";
            if (failUploads != null && failUploads.size() > 0)
                addActionError(getText("uploadAction.upload.failure", new String[]{McedtMessageCreator.stringListToString(failUploads)}));
            if (failSubmits != null && failSubmits.size() > 0)
                addActionError(getText("uploadAction.submit.failure", new String[]{McedtMessageCreator.stringListToString(failSubmits)}));

        } catch (IOException e) {
            logger.error("An IO Exception has occured while moving the files to the sent folder at " + new Date(), e);

            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("uploadAction.upload.submit.failure", new String[]{errorMessage}));
        } catch (Exception e) {
            logger.error("Unable to Upload/Submit file", e);

            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("uploadAction.upload.submit.failure", new String[]{errorMessage}));
        }
        return SUCCESS;
    }

    public String deleteUpload() {
        try {
            List<String> fileNames = Arrays.asList(this.getFileName().trim().split(","));
            OscarProperties props = OscarProperties.getInstance();
            for (String fileName : fileNames) {
                File file = new File(props.getProperty("ONEDT_OUTBOX", "") + fileName);
                file.delete();
            }

        } catch (Exception e) {
            logger.error("Unable to Delete file", e);

            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("uploadAction.upload.submit.failure", new String[]{errorMessage}));
        }
        return SUCCESS;
    }

    public String addUpload() {
        if (!ActionUtils.isOBECFile(this.getFileName()) && !ActionUtils.isOHIPFile(this.getFileName())) {
            addActionError(getText("uploadAction.upload.add.failure", new String[]{this.getFileName() + " is not a supported file Name. Please upload only claim/OBEC files"}));
            return "failure";
        } else {
            OscarProperties props = OscarProperties.getInstance();
            File myFile = new File(props.getProperty("ONEDT_OUTBOX", "") + this.getFileName());
            try (FileOutputStream outputStream = new FileOutputStream(myFile)) {
                outputStream.write(Files.readAllBytes(this.getAddUploadFile().toPath()));
                outputStream.close();
                addActionMessage(getText("uploadAction.upload.add.success", new String[]{this.getFileName() + " is succesfully added to the uploads list!"}));
            } catch (IOException e) {
                logger.error("An error has occured with the addUpload file at " + new Date(), e);

                String errorMessage = McedtMessageCreator.exceptionToString(e);
                addActionError(getText("uploadAction.upload.add.failure", new String[]{errorMessage}));
                return "failure";
            } catch (Exception e) {
                logger.error("Unable to Add file upload", e);

                String errorMessage = McedtMessageCreator.exceptionToString(e);
                addActionError(getText("uploadAction.upload.add.failure", new String[]{errorMessage}));
                return "failure";
            }
        }

        return SUCCESS;

    }

    public UploadData toUpload() {
        UploadData result = new UploadData();
        result.setDescription(this.getDescription());
        result.setResourceType(this.getResourceType());
        OscarProperties props = OscarProperties.getInstance();
        File file = new File(props.getProperty("ONEDT_OUTBOX", "") + this.getFileName());
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[fis.available()];
            fis.read(data);
            fis.close();
            result.setContent(data);
        } catch (Exception e) {
            logger.error("Unable to read upload file", e);

            throw new RuntimeException("Unable to read upload file", e);
        }
        return result;
    }

    public List<UploadData> toUploadMultipe() {
        List<UploadData> results = new ArrayList<UploadData>();
        List<String> fileNames = Arrays.asList(this.getFileName().trim().split(","));
        List<String> resourceTypes = Arrays.asList(this.getResourceType().trim().split(","));
        if (fileNames.size() == resourceTypes.size()) {
            for (int i = 0; i < fileNames.size(); i++) {
                UploadData result = new UploadData();
                result.setDescription(fileNames.get(i));
                result.setResourceType(resourceTypes.get(i));
                OscarProperties props = OscarProperties.getInstance();
                File file = new File(props.getProperty("ONEDT_OUTBOX", "") + fileNames.get(i));
                try (FileInputStream fis = new FileInputStream(file);) {
                    byte[] data = new byte[fis.available()];
                    fis.read(data);
                    fis.close();
                    result.setContent(data);
                    results.add(result);
                } catch (Exception e) {
                    logger.error("Unable to read upload file", e);

                    throw new RuntimeException("Unable to read upload file", e);
                }
            }
        }
        return results;
    }

    private String description;
    private String resourceType;
    private String fileName;
    private BigInteger resourceId;
    private File addUploadFile;
    private String addUploadFileFileName;
    private String addUploadFileContentType;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public BigInteger getResourceId() {
        return resourceId;
    }

    public void setResourceId(BigInteger resourceId) {
        this.resourceId = resourceId;
    }

    public File getAddUploadFile() {
        return addUploadFile;
    }

    public void setAddUploadFile(File addUploadFile) {
        this.addUploadFile = addUploadFile;
    }

    public String getAddUploadFileFileName() {
        return addUploadFileFileName;
    }
    public void setAddUploadFileFileName(String addUploadFileFileName) {
        this.addUploadFileFileName = addUploadFileFileName;
        this.setFileName(addUploadFileFileName); // set the file name to the upload file name
    }

    public String getAddUploadFileContentType() {
        return addUploadFileContentType;
    }
    public void setAddUploadFileContentType(String addUploadFileContentType) {
        this.addUploadFileContentType = addUploadFileContentType;
        this.setResourceType(addUploadFileContentType); // set the resource type to the upload file content type
    }
}