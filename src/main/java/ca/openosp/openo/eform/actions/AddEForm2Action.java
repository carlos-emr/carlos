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


package ca.openosp.openo.eform.actions;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.enumerator.DocumentType;
import ca.openosp.openo.documentManager.DocumentAttachmentManager;
import ca.openosp.openo.email.core.EmailAttachmentSettings;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.EformDataManager;
import ca.openosp.openo.managers.EmailManager;
import ca.openosp.openo.managers.FaxManager.TransactionType;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.match.IMatchManager;
import ca.openosp.openo.match.MatchManager;
import ca.openosp.openo.match.MatchManagerException;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PDFGenerationException;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.eform.EFormLoader;
import ca.openosp.openo.eform.EFormUtil;
import ca.openosp.openo.eform.data.DatabaseAP;
import ca.openosp.openo.eform.data.EForm;
import ca.openosp.openo.encounter.data.EctProgram;
import ca.openosp.openo.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

public class AddEForm2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private EformDataManager eformDataManager = SpringUtils.getBean(EformDataManager.class);
    private DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private EmailManager emailManager = SpringUtils.getBean(EmailManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        logger.debug("==================SAVING ==============");
        HttpSession se = request.getSession();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        boolean fax = "true".equals(request.getParameter("faxEForm"));
        boolean print = "true".equals(request.getParameter("print"));
        boolean saveAsEdoc = "true".equals(request.getParameter("saveAsEdoc"));
        boolean isDownloadEForm = "true".equals(request.getParameter("saveAndDownloadEForm"));
        boolean isEmailEForm = "true".equals(request.getParameter("emailEForm"));

        String[] attachedDocuments = (request.getParameterValues("docNo") != null ? request.getParameterValues("docNo") : new String[0]);
        String[] attachedLabs = (request.getParameterValues("labNo") != null ? request.getParameterValues("labNo") : new String[0]);
        String[] attachedForms = (request.getParameterValues("formNo") != null ? request.getParameterValues("formNo") : new String[0]);
        String[] attachedEForms = (request.getParameterValues("eFormNo") != null ? request.getParameterValues("eFormNo") : new String[0]);
        String[] attachedHRMDocuments = (request.getParameterValues("hrmNo") != null ? request.getParameterValues("hrmNo") : new String[0]);

        @SuppressWarnings("unchecked")
        Enumeration<String> paramNamesE = request.getParameterNames();
        //for each name="fieldname" value="myval"
        ArrayList<String> paramNames = new ArrayList<String>();  //holds "fieldname, ...."
        ArrayList<String> paramValues = new ArrayList<String>(); //holds "myval, ...."
        String fid = request.getParameter("efmfid");
        String demographic_no = request.getParameter("efmdemographic_no");
        String eform_link = request.getParameter("eform_link");
        String subject = request.getParameter("subject");

        /*
         * Part 2 of "counter hack for a hack" initialized in Javascript file
         * eform_floating_toolbar.js
         */
        String[] imagePathPlaceHolders = request.getParameterValues("openosp-image-link");

        /*
         * An eform developer may add these to the eForm in order to auto
         * populate fax information.
         */
        String recipient = request.getParameter("recipient");
        String recipientFaxNumber = request.getParameter("recipientFaxNumber");
        String letterheadFax = request.getParameter("letterheadFax");

        boolean doDatabaseUpdate = false;
        List<String> oscarUpdateFields = new ArrayList<>();
        if (request.getParameter("_oscardodatabaseupdate") != null && request.getParameter("_oscardodatabaseupdate").equalsIgnoreCase("on")) {
            doDatabaseUpdate = true;
        }

        // The fields in the _oscarupdatefields parameter are separated by %s.
        if (!print && !fax && doDatabaseUpdate && request.getParameter("_oscarupdatefields") != null) {

            oscarUpdateFields = Arrays.asList(request.getParameter("_oscarupdatefields").split("%"));

            boolean validationError = false;

            for (String field : oscarUpdateFields) {
                EFormLoader.getInstance();
                // Check for existence of appropriate databaseap
                DatabaseAP currentAP = EFormLoader.getAP(field);
                if (currentAP != null) {
                    if (!currentAP.isInputField()) {
                        // Abort! This field can't be updated
                        addActionError(getText("errors.richeForms.noInputMethodError", new String[]{field}));
                        validationError = true;
                    }
                } else {
                    // Field doesn't exit
                    addActionError(getText("errors.richeForms.noSuchFieldError", new String[]{field}));
                    validationError = true;
                }
            }

            if (!validationError) {
                for (String field : oscarUpdateFields) {
                    EFormLoader.getInstance();
                    DatabaseAP currentAP = EFormLoader.getAP(field);
                    // We can add more of these later...
                    if (currentAP != null) {
                        String inSQL = currentAP.getApInSQL();

                        inSQL = DatabaseAP.parserReplace("demographic", demographic_no, inSQL);
                        inSQL = DatabaseAP.parserReplace("providers", providerNo, inSQL);
                        inSQL = DatabaseAP.parserReplace("fid", fid, inSQL);

                        inSQL = DatabaseAP.parserReplace("value", request.getParameter(field), inSQL);

                        //if(currentAP.getArchive() != null && currentAP.getArchive().equals("demographic")) {
                        //	demographicArchiveDao.archiveRecord(demographicManager.getDemographic(loggedInInfo,demographic_no));
                        //}

                        // Run the SQL query against the database
                        //TODO: do this a different way.
                        MiscUtils.getLogger().error("Error", new Exception("EForm is using disabled functionality for updating fields..update not performed"));
                    }
                }
            }
        }

        if (subject == null) subject = "";
        String curField = "";
        while (paramNamesE.hasMoreElements()) {
            curField = paramNamesE.nextElement();
            if (curField.equalsIgnoreCase("parentAjaxId")) {
                continue;
            }

            /*
             * Remove these parameters from the request after the imagePathPlaceHolders variable is set.
             * These values do not need to be saved into the eform_values table.
             */
            if (curField.equalsIgnoreCase("openosp-image-link")) {
                continue;
            }

            if (request.getParameter(curField) != null && (!request.getParameter(curField).trim().equals(""))) {
                paramNames.add(curField);
                paramValues.add(request.getParameter(curField));
            }

        }

        EForm curForm = new EForm(fid, demographic_no, providerNo);
        curForm.setContextPath(request.getContextPath());
		curForm.setRealPath(request.getServletContext().getRealPath(File.separator));
		curForm.setImagePath();

        //add eform_link value from session attribute
        ArrayList<String> openerNames = curForm.getOpenerNames();
        ArrayList<String> openerValues = new ArrayList<String>();
        for (String name : openerNames) {
            String lnk = providerNo + "_" + demographic_no + "_" + fid + "_" + name;
            String val = (String) se.getAttribute(lnk);
            openerValues.add(val);
            if (val != null) se.removeAttribute(lnk);
        }

        //----names parsed
        //ActionMessages errors = curForm.setMeasurements(paramNames, paramValues);
        curForm.setFormSubject(subject);
        curForm.setValues(paramNames, paramValues);
        if (!openerNames.isEmpty()) curForm.setOpenerValues(openerNames, openerValues);
        if (eform_link != null) curForm.setEformLink(eform_link);
        curForm.setAction();
        curForm.setNowDateTime();
//TODO        if (!errors.isEmpty()) {
//            saveErrors(request, errors);
//            request.setAttribute("curform", curForm);
//            request.setAttribute("page_errors", "true");
//            return mapping.getInputForward();
//        }

        //Check if eform same as previous, if same -> not saved
        String prev_fdid = (String) se.getAttribute("eform_data_id");
        se.removeAttribute("eform_data_id");
        boolean sameform = false;
        if (StringUtils.filled(prev_fdid)) {
            EForm prevForm = new EForm(prev_fdid);
            if (prevForm != null) {
                sameform = curForm.getFormHtml().equals(prevForm.getFormHtml());
            }
        }
        if (!sameform) { //save eform data

            /*
             * Part 2 of "counter hack for a hack" initialized in Javascript file
             * eform_floating_toolbar.js
             * Grab the image path placeholders from the form submission and then
             * feed them into the EForm object.
             * Doing this ensures the image links get saved correctly into the HTML
             * of the eform_data database table.
             */
            try {
                curForm.addImagePathPlaceholders(imagePathPlaceHolders);
            } catch (Exception e) {
                logger.error("Error retrieving image path placeholders from eForm submission.", e);
            }

            String fdid = eformDataManager.saveEformData(loggedInInfo, curForm) + "";

            EFormUtil.addEFormValues(paramNames, paramValues, Integer.valueOf(fdid), Integer.valueOf(fid), Integer.valueOf(demographic_no)); //adds parsed values

            attachToEForm(loggedInInfo, attachedEForms, attachedDocuments, attachedLabs, attachedHRMDocuments, attachedForms, fdid, demographic_no, providerNo);

            //post fdid to {eform_link} attribute
            if (eform_link != null) {
                se.setAttribute(eform_link, fdid);
            }

            request.setAttribute("fdid", fdid);
            request.setAttribute("demographicId", demographic_no);

            if (saveAsEdoc) {
                try {
                    documentAttachmentManager.saveEFormAsEDoc(request, response);
                } catch (PDFGenerationException e) {
                    logger.error(e.getMessage(), e);
                    String errorMessage = "This eForm (and attachments, if applicable) could not be added to this patient’s documents. \\n\\n" + e.getMessage();
                    request.setAttribute("errorMessage", errorMessage);
                    return "error";
                }
            }

            if (fax) {
                StringBuilder faxForward = new StringBuilder(request.getContextPath()).append("/fax/faxAction.do");
                faxForward.append("?method=").append("prepareFax");
                faxForward.append("&transactionId=").append(URLEncoder.encode(fdid, StandardCharsets.UTF_8));
                faxForward.append("&transactionType=").append(URLEncoder.encode(TransactionType.EFORM.name(), StandardCharsets.UTF_8));
                faxForward.append("&demographicNo=").append(URLEncoder.encode(demographic_no, StandardCharsets.UTF_8));

                /*
                 * Added incase the eForm developer adds these elements to the
                 * eform.
                 */
                if (recipient != null && !recipient.isEmpty()) {
                    faxForward.append("&recipient=").append(URLEncoder.encode(recipient, StandardCharsets.UTF_8));
                }
                if (recipientFaxNumber != null && !recipientFaxNumber.isEmpty()) {
                    faxForward.append("&recipientFaxNumber=").append(URLEncoder.encode(recipientFaxNumber, StandardCharsets.UTF_8));
                }
                if (letterheadFax != null && !letterheadFax.isEmpty()) {
                    faxForward.append("&letterheadFax=").append(URLEncoder.encode(letterheadFax, StandardCharsets.UTF_8));
                }
                try {
                    response.sendRedirect(faxForward.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return NONE;
            } else if (print) {
                return "print";
            } else if (isDownloadEForm) {
                /*
                 * For now, this download code is added here and will be moved to the appropriate place after refactoring is done.
                 */
                String fileName = generateFileName(loggedInInfo, Integer.parseInt(demographic_no));
                String pdfBase64 = "";
                try {
                    Path eFormPdfPath = documentAttachmentManager.renderEFormWithAttachments(request, response);
                    pdfBase64 = documentAttachmentManager.convertPDFToBase64(eFormPdfPath);
                } catch (PDFGenerationException e) {
                    logger.error(e.getMessage(), e);
                    String errorMessage = "This eForm (and attachments, if applicable) could not be downloaded. \\n\\n" + e.getMessage();
                    request.setAttribute("errorMessage", errorMessage);
                    return "error";
                }

                request.setAttribute("eFormPDF", pdfBase64);
                request.setAttribute("eFormPDFName", fileName);
                request.setAttribute("isDownload", "true");

                request.setAttribute("fdid", fdid);
                request.setAttribute("parentAjaxId", "eforms");

                return "download";
            } else if (isEmailEForm) {
                String path = request.getContextPath() + "/email/emailComposeAction.do?method=prepareComposeEFormMailer&fid=" + Encode.forUriComponent(fid);
                EmailAttachmentSettings settings = EmailAttachmentSettings.of(
                    request,
                    fdid,
                    demographic_no,
                    attachedEForms,
                    attachedDocuments,
                    attachedLabs,
                    attachedHRMDocuments,
                    attachedForms
                );
                addEmailAttachmentsToSession(request, settings);
                try {
                    response.sendRedirect(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return NONE;
            } else {
                //write template message to echart
                String program_no = new EctProgram(se).getProgram(providerNo);
                String path = request.getRequestURL().toString();
                String uri = request.getRequestURI();
                path = path.substring(0, path.indexOf(uri));
                path += request.getContextPath();

                EFormUtil.writeEformTemplate(LoggedInInfo.getLoggedInInfoFromSession(request), paramNames, paramValues, curForm, fdid, program_no, path);
            }

        } else {
            logger.debug("Warning! Form HTML exactly the same, new form data not saved.");
            request.setAttribute("fdid", prev_fdid);
            request.setAttribute("demographicId", demographic_no);

            attachToEForm(loggedInInfo, attachedEForms, attachedDocuments, attachedLabs, attachedHRMDocuments, attachedForms, prev_fdid, demographic_no, providerNo);

            if (fax) {
                /*
                 * This form id is sent to the fax action to render it as a faxable PDF.
                 * A preview is returned to the user once the form is rendered.
                 */
                StringBuilder faxForward = new StringBuilder(request.getContextPath()).append("/fax/faxAction.do");
                faxForward.append("?method=").append(URLEncoder.encode("prepareFax", StandardCharsets.UTF_8));
                faxForward.append("&transactionId=").append(URLEncoder.encode(prev_fdid, StandardCharsets.UTF_8));
                faxForward.append("&transactionType=").append(URLEncoder.encode(TransactionType.EFORM.name(), StandardCharsets.UTF_8));
                faxForward.append("&demographicNo=").append(URLEncoder.encode(demographic_no, StandardCharsets.UTF_8));


                /*
                 * Added incase the eForm developer adds these elements to the
                 * eform.
                 */
                if (recipient != null) {
                    faxForward.append("&recipient=").append(recipient);
                }
                if (recipientFaxNumber != null) {
                    faxForward.append("&recipientFaxNumber=").append(recipientFaxNumber);
                }
                if (letterheadFax != null) {
                    faxForward.append("&letterheadFax=").append(letterheadFax);
                }
                try {
                    response.sendRedirect(faxForward.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return NONE;
            } else if (print) {
                return "print";
            } else if (isDownloadEForm) {
                /*
                 * For now, this download code is added here and will be moved to the appropriate place after refactoring is done.
                 */
                String fileName = generateFileName(loggedInInfo, Integer.parseInt(demographic_no));
                String pdfBase64 = "";
                try {
                    Path eFormPdfPath = documentAttachmentManager.renderEFormWithAttachments(request, response);
                    pdfBase64 = documentAttachmentManager.convertPDFToBase64(eFormPdfPath);
                } catch (PDFGenerationException e) {
                    logger.error(e.getMessage(), e);
                    String errorMessage = "This eForm (and attachments, if applicable) could not be downloaded. \\n\\n" + e.getMessage();
                    request.setAttribute("errorMessage", errorMessage);
                    return "error";
                }

                request.setAttribute("eFormPDF", pdfBase64);
                request.setAttribute("eFormPDFName", fileName);
                request.setAttribute("isDownload", "true");

                request.setAttribute("fdid", prev_fdid);
                request.setAttribute("parentAjaxId", "eforms");

                return "download";
            } else if (isEmailEForm) {
                String path = request.getContextPath() + "/email/emailComposeAction.do?method=prepareComposeEFormMailer&fid=" + Encode.forUriComponent(fid);
                EmailAttachmentSettings settings = EmailAttachmentSettings.of(
                    request,
                    prev_fdid,
                    demographic_no,
                    attachedEForms,
                    attachedDocuments,
                    attachedLabs,
                    attachedHRMDocuments,
                    attachedForms
                );
                addEmailAttachmentsToSession(request, settings);
                try {
                    response.sendRedirect(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return NONE;
            }

            if (saveAsEdoc) {
                try {
                    documentAttachmentManager.saveEFormAsEDoc(request, response);
                } catch (PDFGenerationException e) {
                    logger.error(e.getMessage(), e);
                    String errorMessage = "This eForm (and attachments, if applicable) could not be added to this patient’s documents. \\n\\n" + e.getMessage();
                    request.setAttribute("errorMessage", errorMessage);
                    return "error";
                }
            }
        }

        if (demographic_no != null) {
            IMatchManager matchManager = new MatchManager();
            DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
            Demographic client = demographicManager.getDemographic(loggedInInfo, demographic_no);
            try {
                matchManager.<Demographic>processEvent(client, IMatchManager.Event.CLIENT_CREATED);
            } catch (MatchManagerException e) {
                MiscUtils.getLogger().error("Error while processing MatchManager.processEvent(Client)", e);
            }
		}

        String fdid = (String) request.getAttribute("fdid");

		String pdfBase64;
		try {
			Path eFormPdfPath = documentAttachmentManager.renderEFormWithAttachments(request, response);
			pdfBase64 = documentAttachmentManager.convertPDFToBase64(eFormPdfPath);
		} catch (PDFGenerationException e) {
			logger.error(e.getMessage(), e);
			String errorMessage = "This eForm (and attachments, if applicable) could not be downloaded. \\n\\n" + e.getMessage();
			request.setAttribute("errorMessage", errorMessage);
			return "error";
		}

		request.setAttribute("eFormPDF", pdfBase64);
		request.setAttribute("eFormPDFName", generateFileName(loggedInInfo, Integer.parseInt(demographic_no)));
		request.setAttribute("isSuccess_Autoclose", "true");

        request.setAttribute("fdid", fdid);
        request.setAttribute("parentAjaxId", "eforms");

        return "close";
	}
	
	private String generateFileName(LoggedInInfo loggedInInfo, int demographicNo) {
		DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
		String demographicLastName = demographicManager.getDemographicFormattedName(loggedInInfo, demographicNo).split(", ")[0];

        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd");
        String formattedDate = dateFormat.format(currentDate);

        return formattedDate + "_" + demographicLastName + ".pdf";
    }

    /**
     * Stores email attachment data in session for use after redirect.
     * Session attributes survive redirects, unlike request attributes.
     *
     * @param request HTTP request
     * @param settings EmailAttachmentSettings containing all attachment configuration
     */
    private void addEmailAttachmentsToSession(HttpServletRequest request, EmailAttachmentSettings settings) {
        HttpSession session = request.getSession();
        session.setAttribute("deleteEFormAfterEmail", settings.deleteEFormAfterEmail());
        session.setAttribute("isEmailEncrypted", settings.isEmailEncrypted());
        session.setAttribute("isEmailAttachmentEncrypted", settings.isEmailAttachmentEncrypted());
        session.setAttribute("isEmailAutoSend", settings.isEmailAutoSend());
        session.setAttribute("openEFormAfterEmail", settings.openAfterEmail());
        session.setAttribute("attachEFormItSelf", settings.attachEFormItSelf());
        session.setAttribute("fdid", settings.fdid());
        session.setAttribute("demographicId", settings.demographicNo());
        session.setAttribute("attachedEForms", settings.attachedEForms());
        session.setAttribute("attachedDocuments", settings.attachedDocuments());
        session.setAttribute("attachedLabs", settings.attachedLabs());
        session.setAttribute("attachedHRMDocuments", settings.attachedHRMDocuments());
        session.setAttribute("attachedForms", settings.attachedForms());
        session.setAttribute("emailPDFPassword", settings.emailPDFPassword());
        session.setAttribute("emailPDFPasswordClue", settings.emailPDFPasswordClue());
        session.setAttribute("senderEmail", settings.senderEmail());
        session.setAttribute("subjectEmail", settings.subjectEmail());
        session.setAttribute("bodyEmail", settings.bodyEmail());
        session.setAttribute("encryptedMessageEmail", settings.encryptedMessageEmail());
        session.setAttribute("emailPatientChartOption", settings.emailPatientChartOption());
    }

    /**
     * Stores email attachment data in request attributes.
     * Used for non-redirect scenarios where request scope is sufficient.
     *
     * @param request HTTP request
     * @param settings EmailAttachmentSettings containing all attachment configuration
     */
    private void addEmailAttachments(HttpServletRequest request, EmailAttachmentSettings settings) {
        request.setAttribute("deleteEFormAfterEmail", settings.deleteEFormAfterEmail());
        request.setAttribute("isEmailEncrypted", settings.isEmailEncrypted());
        request.setAttribute("isEmailAttachmentEncrypted", settings.isEmailAttachmentEncrypted());
        request.setAttribute("isEmailAutoSend", settings.isEmailAutoSend());
        request.setAttribute("openEFormAfterEmail", settings.openAfterEmail());
        request.setAttribute("attachEFormItSelf", settings.attachEFormItSelf());
        request.setAttribute("fdid", settings.fdid());
        request.setAttribute("demographicId", settings.demographicNo());
        request.setAttribute("attachedEForms", settings.attachedEForms());
        request.setAttribute("attachedDocuments", settings.attachedDocuments());
        request.setAttribute("attachedLabs", settings.attachedLabs());
        request.setAttribute("attachedHRMDocuments", settings.attachedHRMDocuments());
        request.setAttribute("attachedForms", settings.attachedForms());
        request.setAttribute("emailPDFPassword", settings.emailPDFPassword());
        request.setAttribute("emailPDFPasswordClue", settings.emailPDFPasswordClue());
        request.setAttribute("senderEmail", settings.senderEmail());
        request.setAttribute("subjectEmail", settings.subjectEmail());
        request.setAttribute("bodyEmail", settings.bodyEmail());
        request.setAttribute("encryptedMessageEmail", settings.encryptedMessageEmail());
        request.setAttribute("emailPatientChartOption", settings.emailPatientChartOption());
    }

    private void attachToEForm(LoggedInInfo loggedInInfo, String[] attachedEForms, String[] attachedDocuments, String[] attachedLabs, String[] attachedHRMDocuments, String[] attachedForms, String fdid, String demographic_no, String providerNo) {
        documentAttachmentManager.attachToEForm(loggedInInfo, DocumentType.DOC, attachedDocuments, providerNo, Integer.valueOf(fdid), Integer.valueOf(demographic_no));
        documentAttachmentManager.attachToEForm(loggedInInfo, DocumentType.LAB, attachedLabs, providerNo, Integer.valueOf(fdid), Integer.valueOf(demographic_no));
        documentAttachmentManager.attachToEForm(loggedInInfo, DocumentType.FORM, attachedForms, providerNo, Integer.valueOf(fdid), Integer.valueOf(demographic_no));
        documentAttachmentManager.attachToEForm(loggedInInfo, DocumentType.EFORM, attachedEForms, providerNo, Integer.valueOf(fdid), Integer.valueOf(demographic_no));
        documentAttachmentManager.attachToEForm(loggedInInfo, DocumentType.HRM, attachedHRMDocuments, providerNo, Integer.valueOf(fdid), Integer.valueOf(demographic_no));
    }

}
