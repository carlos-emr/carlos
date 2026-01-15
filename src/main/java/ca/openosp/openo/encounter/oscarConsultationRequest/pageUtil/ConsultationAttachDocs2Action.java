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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.itextpdf.text.DocumentException;
import com.sun.xml.messaging.saaj.util.ByteOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.ConsultDocs;
import ca.openosp.openo.commn.model.EFormData;
import ca.openosp.openo.hospitalReportManager.HRMUtil;
import ca.openosp.openo.hospitalReportManager.HRMPDFCreator;
import ca.openosp.openo.managers.ConsultationManager;
import ca.openosp.openo.managers.FaxManager;
import ca.openosp.openo.managers.FormsManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.documentManager.EDoc;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.eform.EFormUtil;
import ca.openosp.openo.form.util.FormTransportContainer;
import ca.openosp.openo.encounter.data.EctFormData;
import ca.openosp.openo.lab.ca.all.pageUtil.LabPDFCreator;
import ca.openosp.openo.lab.ca.on.CommonLabResultData;
import ca.openosp.openo.lab.ca.on.LabResultData;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ConsultationAttachDocs2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private final Logger logger = MiscUtils.getLogger();
    FaxManager faxManager = SpringUtils.getBean(FaxManager.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() {
        return fetchAll();
    }

    @SuppressWarnings("unused")
    public String fetchAll() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = request.getParameter("demographicNo");
        String requestId = request.getParameter("requestId");
        FormsManager formsManager = SpringUtils.getBean(FormsManager.class);
        ConsultationManager consultationManager = SpringUtils.getBean(ConsultationManager.class);

        //Get all LAB information for demographic, along with which are already attached
        List<String> attachedLabIds = new ArrayList<String>();
        CommonLabResultData commonLabResultData = new CommonLabResultData();
        List<LabResultData> allLabs = commonLabResultData.populateLabResultsData(loggedInInfo, "", demographicNo, "", "", "", "U");
        Collections.sort(allLabs);
        List<LabResultData> attachedLabs = commonLabResultData.populateLabResultsData(loggedInInfo, demographicNo, requestId, CommonLabResultData.ATTACHED);
        if (attachedLabs != null) {
            for (LabResultData labResultData : attachedLabs) {
                attachedLabIds.add(labResultData.segmentID);
            }
        }

        //Get all DOCUMENT information for demographic, along with which are already attached
        List<String> attachedDocumentIds = new ArrayList<String>();
        List<EDoc> allDocuments = EDocUtil.listDocs(loggedInInfo, "demographic", demographicNo, null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE);
        List<EDoc> attachedDocuments = EDocUtil.listDocs(loggedInInfo, demographicNo, requestId, EDocUtil.ATTACHED);
        if (attachedDocuments != null) {
            for (EDoc document : attachedDocuments) {
                attachedDocumentIds.add(document.getDocId());
            }
        }

        //Get all FORM information for demographic, along with which are already attached
        List<String> attachedFormIds = new ArrayList<String>();
        List<EctFormData.PatientForm> allForms = formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, Integer.parseInt(demographicNo), false, true);
        List<EctFormData.PatientForm> attachedForms = null;
        if (requestId != null && !requestId.isEmpty() && !"null".equals(requestId)) {
            attachedForms = consultationManager.getAttachedForms(loggedInInfo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
            if (attachedForms != null) {
                for (EctFormData.PatientForm attachedForm : attachedForms) {
                    attachedFormIds.add(attachedForm.formId + "");
                }
            }
        }

        //Get all EFORM information for demographic, along with which are already attached
        List<String> attachedEFormIds = new ArrayList<String>();
        List<EFormData> allEForms = EFormUtil.listPatientEformsCurrent(Integer.valueOf(demographicNo), true);
        List<EFormData> attachedEForms = null;
        if (requestId != null && !requestId.isEmpty() && !"null".equals(requestId)) {
            attachedEForms = consultationManager.getAttachedEForms(requestId);
            if (attachedEForms != null) {
                for (EFormData attachedEForm : attachedEForms) {
                    attachedEFormIds.add(attachedEForm.getId() + "");
                }
            }
        }

        //Get all HRM information for demographic, along with which are already attached
        List<String> attachedHRMDocumentIds = new ArrayList<String>();
        ArrayList<HashMap<String, ? extends Object>> allHRMDocuments = HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, demographicNo, false);
        List<ConsultDocs> attachedHRMDocuments = null;
        if (requestId != null && !requestId.isEmpty() && !"null".equals(requestId)) {
            attachedHRMDocuments = consultationManager.getAttachedDocumentsByType(loggedInInfo, Integer.parseInt(requestId), ConsultDocs.DOCTYPE_HRM);
            if (attachedHRMDocuments != null) {
                for (ConsultDocs consultDocs : attachedHRMDocuments) {
                    attachedHRMDocumentIds.add(String.valueOf(consultDocs.getDocumentNo()));
                }
            }
        }

        request.setAttribute("attachedDocumentIds", attachedDocumentIds);
        request.setAttribute("attachedLabIds", attachedLabIds);
        request.setAttribute("attachedFormIds", attachedFormIds);
        request.setAttribute("attachedEFormIds", attachedEFormIds);
        request.setAttribute("attachedHRMDocumentIds", attachedHRMDocumentIds);


        request.setAttribute("allDocuments", allDocuments);
        request.setAttribute("allLabs", allLabs);
        request.setAttribute("allForms", allForms);
        request.setAttribute("allEForms", allEForms);
        request.setAttribute("allHRMDocuments", allHRMDocuments);

        return "fetchAll";
    }

    public void getDocumentPDF() {
        //TODO: refactor this function, and similar code in EctConsultationFormRequestPrincAction2.java
        //      and EctConsultationFormFax2Action.java as part of extending this attach item functionality
        //      to eforms and ticklers

        String isImage = request.getParameter("isImage");
        String isPDF = request.getParameter("isPDF");
        String fileName = request.getParameter("fileName");
        String description = request.getParameter("description");

        // Validate and sanitize the file path to prevent path traversal attacks
        Path validatedPath = validateDocumentPath(fileName);
        if (validatedPath == null) {
            logger.error("Invalid file path requested: " + fileName);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String fullPath = validatedPath.toString();
        request.setAttribute("imagePath", fullPath);
        request.setAttribute("imageTitle", description);

        if (Objects.equals(isImage, "true")) {
            try (ByteOutputStream byteOutputStream = new ByteOutputStream()) {
                ImagePDFCreator imagePDFCreator = new ImagePDFCreator(request, byteOutputStream);
                imagePDFCreator.printPdf();
                generateResponse(response, getBase64(byteOutputStream.getBytes()));
            } catch (DocumentException | IOException e) {
                logger.error("An error occurred while creating the pdf of the image: " + e.getMessage(), e);
            }
        } else if (Objects.equals(isPDF, "true")) {
            generateResponse(response, getBase64(validatedPath));
        }
    }

    public void getLabPDF() {
        //TODO: refactor this function, and similar code in EctConsultationFormRequestPrincAction2.java
        //      and EctConsultationFormFax2Action.java as part of extending this attach item functionality
        //      to eforms and ticklers

        String segmentID = request.getParameter("segmentID");
        request.setAttribute("segmentID", segmentID);
        try {
            File tempLabPDF = File.createTempFile("lab" + segmentID, "pdf");
            try (
                    FileOutputStream fileOutputStream = new FileOutputStream(tempLabPDF);
                    ByteOutputStream byteOutputStream = new ByteOutputStream();
            ) {
                LabPDFCreator labPDFCreator = new LabPDFCreator(request, fileOutputStream);
                labPDFCreator.printPdf();
                labPDFCreator.addEmbeddedDocuments(tempLabPDF, byteOutputStream);
                generateResponse(response, getBase64(byteOutputStream.getBytes()));
            }
            tempLabPDF.delete();
        } catch (DocumentException | IOException e) {
            logger.error("An error occurred: " + e.getMessage(), e);
        }
    }

    public void getFormPDF() {
        //TODO: refactor this function, and similar code in EctConsultationFormRequestPrincAction2.java
        //      and EctConsultationFormFax2Action.java as part of extending this attach item functionality
        //      to eforms and ticklers

        String formId = request.getParameter("formId");
        String formName = request.getParameter("formName");
        String demographicNo = request.getParameter("demographicNo");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        try {
            FormTransportContainer formTransportContainer = new FormTransportContainer(
                    response, request, "/form/forwardshortcutname.jsp"
                    + "?method=fetch&formname="
                    + formName
                    + "&demographic_no="
                    + demographicNo
                    + "&formId="
                    + formId);
            formTransportContainer.setDemographicNo(demographicNo);
            formTransportContainer.setProviderNo(loggedInInfo.getLoggedInProviderNo());
            formTransportContainer.setSubject(formName + " Form ID " + formId);
            formTransportContainer.setFormName(formName);
            formTransportContainer.setRealPath(ServletActionContext.getServletContext().getRealPath(File.separator));
            Path formPDF = faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.FORM, formTransportContainer);
            generateResponse(response, getBase64(formPDF));
        } catch (ServletException | IOException e) {
            logger.error("An error occurred while processing the form: " + e.getMessage(), e);
        }
    }

    public void getEFormPDF() {
        //TODO: refactor this function, and similar code in EctConsultationFormRequestPrincAction2.java
        //      and EctConsultationFormFax2Action.java as part of extending this attach item functionality
        //      to eforms and ticklers

        String eFormId = request.getParameter("eFormId");
        String demographicNo = request.getParameter("demographicNo");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Path eFormPDF = faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.EFORM, Integer.parseInt(eFormId), Integer.parseInt(demographicNo));
        generateResponse(response, getBase64(eFormPDF));
    }

    public void getHRMPDF() {
        //TODO: refactor this function, and similar code in EctConsultationFormRequestPrincAction2.java
        //      and EctConsultationFormFax2Action.java as part of extending this attach item functionality
        //      to eforms and ticklers

        String hrmId = request.getParameter("hrmId");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        try (ByteOutputStream byteOutputStream = new ByteOutputStream()) {
            HRMPDFCreator hrmPdf = new HRMPDFCreator(byteOutputStream, Integer.parseInt(hrmId), loggedInInfo);
            hrmPdf.printPdf();
            generateResponse(response, getBase64(byteOutputStream.getBytes()));
        }
    }

    private void generateResponse(HttpServletResponse response, String base64Data) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("base64Data", base64Data);
        response.setContentType("text/javascript");
        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            logger.error("An error occurred while writing JSON response to the output stream: " + e.getMessage(), e);
        }
    }

    private String getBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String getBase64(Path pdfPath) {
        try {
            return getBase64(Files.readAllBytes(pdfPath));
        } catch (IOException e) {
            logger.error("An error occurred while processing the PDF file: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Validates and sanitizes a document file path to prevent path traversal attacks.
     * Ensures the file is within the configured DOCUMENT_DIR directory.
     * 
     * @param fileName The file name to validate
     * @return The validated absolute path, or null if invalid
     */
    private Path validateDocumentPath(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            logger.error("Invalid file name: null or empty");
            return null;
        }

        // Reject any file name containing path traversal sequences
        if (fileName.contains("..") || fileName.contains(File.separator) || fileName.contains("/") || fileName.contains("\\")) {
            logger.error("Path traversal attempt detected in file name: " + fileName);
            return null;
        }

        try {
            // Get the configured document directory
            String documentDir = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
            if (documentDir == null || documentDir.trim().isEmpty()) {
                logger.error("DOCUMENT_DIR not configured in properties");
                return null;
            }

            // Validate file path using PathValidationUtils
            File baseDirFile = new File(documentDir);
            File validatedFile;
            try {
                validatedFile = PathValidationUtils.validatePath(fileName, baseDirFile);
            } catch (SecurityException e) {
                logger.error("Path traversal attempt: resolved path escapes base directory");
                return null;
            }
            Path filePath = validatedFile.toPath();
            
            // Verify the file exists and is a regular file
            if (!Files.exists(filePath)) {
                logger.warn("Document file does not exist: " + fileName);
                return null;
            }
            
            if (!Files.isRegularFile(filePath)) {
                logger.error("Path is not a regular file: " + fileName);
                return null;
            }
            
            return filePath;
        } catch (Exception e) {
            logger.error("Error validating document path for file: " + fileName, e);
            return null;
        }
    }
}
