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


package io.github.carlos_emr.carlos.eform.actions;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.core.EmailAttachmentSettings;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.match.IMatchManager;
import io.github.carlos_emr.carlos.match.MatchManager;
import io.github.carlos_emr.carlos.match.MatchManagerException;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class AddEForm2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private static final String INVALID_FILENAME_MESSAGE_KEY = "dms.error.invalidFilename";
    private static final String ERROR_ATTRIBUTE = "error";
    private static final String PDF_DOWNLOAD_FAILURE_MESSAGE = "This eForm (and attachments, if applicable) could not be downloaded.";
    private static final String PDF_DOWNLOAD_MISSING_CONTENT_MESSAGE =
            "Some content of this eForm could not be rendered. Review the omissions below before downloading it.";
    private static final String PDF_PREVIEW_WARNING_MESSAGE = "This eForm was saved, but its PDF preview could not be generated.";
    private static final String ERROR_MESSAGE_ATTRIBUTE = "errorMessage";
    private static final String WARNING_MESSAGE_ATTRIBUTE = "warningMessage";
    private static final String PDF_PREVIEW_FALLBACK_SUFFIX = "_eform.pdf";

    /**
     * Validates the eform_link parameter format to prevent session attribute injection (CWE-501).
     * Expected format: {providerNo}_{demographicNo}_{fid}_{fieldName}
     * Example: "999998_12345_67_referralForm"
     *
     * <p>Demographic number allows {@code -1} for admin-view eform linking
     * (see {@link io.github.carlos_emr.carlos.eform.EFormLoader#getOpenEform}).</p>
     */
    static final Pattern EFORM_LINK_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]{1,6}_(-1|\\d{1,10})_\\d{1,10}_[a-zA-Z0-9_.-]{1,50}$");

    /**
     * Validates an eform_link value against the expected key format.
     *
     * <p>Returns the value unchanged if it matches the expected format, or {@code null}
     * if the value is invalid or null. Non-null invalid values are logged at WARN level.</p>
     *
     * @param eformLink the raw eform_link parameter value (may be null)
     * @return the validated eform_link, or null if invalid
     */
    static String validateEformLink(String eformLink) {
        if (eformLink != null && !EFORM_LINK_PATTERN.matcher(eformLink).matches()) {
            logger.warn("Invalid eform_link parameter rejected: {}", LogSafe.sanitize(eformLink));
            return null;
        }
        return eformLink;
    }

    static String validateTemplateFileName(String rawFileName) {
        if (rawFileName == null || rawFileName.isEmpty()) {
            return rawFileName;
        }
        return PathValidationUtils.validateFileName(rawFileName);
    }

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private EformDataManager eformDataManager = SpringUtils.getBean(EformDataManager.class);
    private DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private EmailManager emailManager = SpringUtils.getBean(EmailManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() {

        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

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
        String eform_link = validateEformLink(request.getParameter("eform_link"));

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
		curForm.setImagePath(request.getContextPath());
        String validatedTemplateFileName;
        try {
            validatedTemplateFileName = validateTemplateFileName(curForm.getFormFileName());
        } catch (FileValidationException e) {
            request.setAttribute(ERROR_ATTRIBUTE, "true");
            request.setAttribute(ERROR_MESSAGE_ATTRIBUTE, getInvalidFilenameMessage());
            logger.warn("Rejected invalid eForm template filename");
            return ERROR;
        }
        if (validatedTemplateFileName != null && !validatedTemplateFileName.isEmpty()) {
            curForm.setFormFileName(validatedTemplateFileName);
        }

        //add eform_link value from session attribute
        ArrayList<String> openerNames = curForm.getOpenerNames();
        ArrayList<String> openerValues = new ArrayList<String>();
        for (String name : openerNames) {
            String lnk = providerNo + "_" + demographic_no + "_" + fid + "_" + name;
            // Validate constructed key before session access (CWE-501 read-path)
            if (validateEformLink(lnk) == null) {
                openerValues.add(null);
                continue;
            }
            String val = (String) se.getAttribute(lnk); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- key is validated by validateEformLink()
            openerValues.add(val);
            if (val != null) se.removeAttribute(lnk); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- session cleanup
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

        // Check if eform same as previous, if same -> not saved.
        //
        // Dead in practice: nothing in the repository ever SETS "eform_data_id" — these two lines
        // are its only references — so prev_fdid is always null and sameform always false. The
        // entire `else` branch below (including its own eDoc/approval handling) is therefore
        // unreachable. Left in place rather than deleted because removing it is a behavioural
        // decision about a duplicate-submission guard that was evidently once wired up.
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
                // Validate eform_link against expected prefix to prevent session key injection (CWE-501).
                // The expected key format is: providerNo_demographicNo_fid_openerName
                String expectedPrefix = providerNo + "_" + demographic_no + "_" + fid + "_";
                if (eform_link.startsWith(expectedPrefix) && eform_link.length() <= 100) {
                    se.setAttribute(eform_link, fdid); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): fdid is Integer.parseInt-validated queue document ID; key validated by validateEformLink()
                } else {
                    logger.warn("Invalid eform_link rejected: {}", LogSafe.sanitize(eform_link)); // nosemgrep: crlf-injection-logs-deepsemgrep -- sanitized via LogSafe (OWASP Encode.forJava) // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                }
            }

            request.setAttribute("fdid", fdid);
            request.setAttribute("demographicId", demographic_no);

            // Runs BEFORE the saveAsEdoc block below, and that ordering is the whole point.
            //
            // This is the eForm's template write: the CPP and encounter notes (EncounterNote,
            // SocHistory, FamHistory, MedHistory, OngoingConcerns, RiskFactors, Reminders, OMeds)
            // plus any template-declared document, prevention, message, tickler or consult request.
            // It used to sit in the final `else` of the workflow chain below, which the eDoc branch's
            // approval return jumped straight over — so an eForm saved as an eDoc, refused by the
            // completeness gate and then approved by the clinician, created the eDoc, reported
            // success, auto-closed, and left the chart notes permanently unwritten. Nothing re-runs
            // them: writeEformTemplate assigns a fresh UUID and persists unconditionally, so it is
            // not idempotent and a later retry would duplicate rather than reconcile.
            //
            // The condition reproduces that `else` exactly — fax, print, download and email each
            // return before reaching it, and each has its own reason not to write the template.
            // Hoisting this WITHOUT the condition would run it on those paths too and duplicate
            // every note, which is a worse defect than the one being fixed here.
            if (!fax && !print && !isDownloadEForm && !isEmailEForm) {
                //write template message to echart
                String program_no = new EctProgram(se).getProgram(providerNo);
                String path = request.getRequestURL().toString();
                String uri = request.getRequestURI();
                path = path.substring(0, path.indexOf(uri));
                path += request.getContextPath();

                EFormUtil.writeEformTemplate(LoggedInInfo.getLoggedInInfoFromSession(request), paramNames, paramValues, curForm, fdid, program_no, path);
            }

            if (saveAsEdoc) {
                try {
                    documentAttachmentManager.saveEFormAsEDoc(request, response);
                } catch (EformContentUnavailableException e) {
                    // Subclass before superclass, same as the download branches: swallowed by the
                    // general handler this was a dead end with no way to review and proceed.
                    return offerEDocApproval(loggedInInfo, e, (String) request.getAttribute("fdid"), demographic_no);
                } catch (PDFGenerationException e) {
                    setPdfError("This eForm (and attachments, if applicable) could not be added to this patient’s documents.", e);
                    return "error";
                }
            }

            if (fax) {
                redirectToPreparedFax(fdid, demographic_no, recipient, recipientFaxNumber, letterheadFax);
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
                    EformDataManager.EformPdfRender rendered = documentAttachmentManager
                            .renderEFormPacketWithCompleteness(request, response, null);
                    pdfBase64 = documentAttachmentManager.convertPDFToBase64(rendered.path());
                    // Advisory conditions deliver the PDF rather than blocking it, so the reader
                    // must still be told the render reported something. Count only: console and
                    // dialog text are form-authored and can carry PHI.
                    request.setAttribute("advisoryIssues", rendered.completeness().advisoryIssueCount());
                } catch (EformContentUnavailableException e) {
                    // MUST precede the PDFGenerationException catch below: this is a subclass, and
                    // being swallowed by the general handler is exactly why an incomplete download
                    // was a dead end with no way for the clinician to review the omissions and
                    // decide. Mirrors the fax path.
                    return offerDownloadApproval(loggedInInfo, e, fdid, demographic_no);
                } catch (PDFGenerationException e) {
                    setPdfError(PDF_DOWNLOAD_FAILURE_MESSAGE, e);
                    return "error";
                }

                request.setAttribute("eFormPDF", pdfBase64);
                request.setAttribute("eFormPDFName", fileName);
                request.setAttribute("isDownload", "true");

                request.setAttribute("fdid", fdid);
                request.setAttribute("parentAjaxId", "eforms");

                return "download";
            } else if (isEmailEForm) {
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
                redirectToEmailCompose(fid);
                return NONE;
            }
            // No trailing `else`: the template write it used to hold now runs above, before the
            // saveAsEdoc block, so no early return can skip it.

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
                redirectToPreparedFax(prev_fdid, demographic_no, recipient, recipientFaxNumber, letterheadFax);
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
                    EformDataManager.EformPdfRender rendered = documentAttachmentManager
                            .renderEFormPacketWithCompleteness(request, response, null);
                    pdfBase64 = documentAttachmentManager.convertPDFToBase64(rendered.path());
                    // Advisory conditions deliver the PDF rather than blocking it, so the reader
                    // must still be told the render reported something. Count only: console and
                    // dialog text are form-authored and can carry PHI.
                    request.setAttribute("advisoryIssues", rendered.completeness().advisoryIssueCount());
                } catch (EformContentUnavailableException e) {
                    // Same subclass-before-superclass ordering as the save branch above.
                    return offerDownloadApproval(loggedInInfo, e, prev_fdid, demographic_no);
                } catch (PDFGenerationException e) {
                    setPdfError(PDF_DOWNLOAD_FAILURE_MESSAGE, e);
                    return "error";
                }

                request.setAttribute("eFormPDF", pdfBase64);
                request.setAttribute("eFormPDFName", fileName);
                request.setAttribute("isDownload", "true");

                request.setAttribute("fdid", prev_fdid);
                request.setAttribute("parentAjaxId", "eforms");

                return "download";
            } else if (isEmailEForm) {
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
                redirectToEmailCompose(fid);
                return NONE;
            }

            if (saveAsEdoc) {
                try {
                    documentAttachmentManager.saveEFormAsEDoc(request, response);
                } catch (EformContentUnavailableException e) {
                    // Subclass before superclass, same as the download branches: swallowed by the
                    // general handler this was a dead end with no way to review and proceed.
                    return offerEDocApproval(loggedInInfo, e, (String) request.getAttribute("fdid"), demographic_no);
                } catch (PDFGenerationException e) {
                    setPdfError("This eForm (and attachments, if applicable) could not be added to this patient’s documents.", e);
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
        return closeWithPdfPreview(loggedInInfo, demographic_no, fdid);
	}
	
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin fax action path built from the current context path with encoded query parameters.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin fax action path built from the current context path with encoded query parameters")
    private void redirectToPreparedFax(String fdid, String demographicNo, String recipient, String recipientFaxNumber, String letterheadFax) {
        StringBuilder faxForward = new StringBuilder(request.getContextPath()).append("/fax/faxAction");
        faxForward.append("?method=").append("prepareFax");
        faxForward.append("&transactionId=").append(URLEncoder.encode(fdid, StandardCharsets.UTF_8));
        faxForward.append("&transactionType=").append(URLEncoder.encode(TransactionType.EFORM.name(), StandardCharsets.UTF_8));
        faxForward.append("&demographicNo=").append(URLEncoder.encode(demographicNo, StandardCharsets.UTF_8));

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
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin email compose path built from the current context path with an encoded eForm id.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin email compose path built from the current context path with an encoded eForm id")
    private void redirectToEmailCompose(String fid) {
        String path = request.getContextPath() + "/email/emailComposeAction?method=prepareComposeEFormMailer&fid=" + SafeEncode.forUriComponent(fid);
        try {
            response.sendRedirect(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	private String generateFileName(LoggedInInfo loggedInInfo, int demographicNo) {
		DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
		// Null-tolerant: getDemographicFormattedName returns null when the demographic row is
		// missing, and this runs after the eForm has already been saved and rendered — an NPE here
		// would discard a completed save over a filename.
		String formattedName = demographicManager.getDemographicFormattedName(loggedInInfo, demographicNo);
		String demographicLastName = formattedName == null || formattedName.isBlank()
				? "eform"
				: formattedName.split(", ")[0];

        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd");
        String formattedDate = dateFormat.format(currentDate);

        return formattedDate + "_" + demographicLastName + ".pdf";
    }

    String closeWithPdfPreview(LoggedInInfo loggedInInfo, String demographicNo, String fdid) {
        String pdfBase64 = "";
        try {
            Path eFormPdfPath = documentAttachmentManager.renderEFormWithAttachments(request, response);
            if (eFormPdfPath == null) {
                throw new PDFGenerationException("eForm PDF preview path was not generated");
            }
            pdfBase64 = documentAttachmentManager.convertPDFToBase64(eFormPdfPath);
        } catch (Exception e) {
            setPdfWarning(PDF_PREVIEW_WARNING_MESSAGE, e);
        }

        request.setAttribute("eFormPDF", pdfBase64);
        request.setAttribute("eFormPDFName", buildPdfPreviewName(loggedInInfo, demographicNo));
        request.setAttribute("isSuccess_Autoclose", "true");
        request.setAttribute("fdid", fdid);
        request.setAttribute("parentAjaxId", "eforms");
        return "close";
    }

    private String buildPdfPreviewName(LoggedInInfo loggedInInfo, String demographicNo) {
        try {
            return generateFileName(loggedInInfo, Integer.parseInt(demographicNo));
        } catch (RuntimeException e) {
            logger.warn("Falling back to a generic PDF preview filename for demographic {}", LogSafe.sanitize(demographicNo), e);
            return new SimpleDateFormat("yyyy_MM_dd").format(new Date()) + PDF_PREVIEW_FALLBACK_SUFFIX;
        }
    }

    /**
     * Offers the clinician an exact, one-time approval for a download the completeness gate refused.
     *
     * <p>Mirrors the fax path. The retry deliberately targets {@code eform/downloadEFormPdf} rather
     * than resubmitting this action: {@code saveEformData} persists a NEW eForm on every submit, so
     * re-posting to approve a render would duplicate the saved record — and would put every form
     * field, patient data included, into the approval page as hidden inputs. The eForm is already
     * saved by this point; only the render failed.</p>
     *
     * <p>Every category the report carries is surfaced. The approval digest binds to the complete
     * issue set, so a category the clinician was never shown is one they cannot meaningfully have
     * approved.</p>
     */
    private String offerDownloadApproval(LoggedInInfo loggedInInfo, EformContentUnavailableException e,
            String fdid, String demographicNo) {
        return offerRenderApproval(loggedInInfo, e, fdid, demographicNo,
                EFormRenderApprovalService.Operation.DOWNLOAD, PDF_DOWNLOAD_MISSING_CONTENT_MESSAGE,
                "eform/downloadEFormPdf", "Approve listed issues and download");
    }

    /**
     * Offers the clinician an exact, one-time approval for a render the completeness gate refused.
     *
     * <p>Shared by the download and save-as-eDoc paths. The retry always targets a render-only or
     * archive-only route rather than resubmitting this action: {@code saveEformData} persists a NEW
     * eForm on every submit, so re-posting to approve a render would duplicate the saved record and
     * would put every form field, patient data included, into the approval page as hidden inputs.
     * The eForm is already saved by this point; only rendering failed.</p>
     *
     * <p>Every category the report carries is published. The approval digest binds to the complete
     * issue set, so a category the clinician was never shown is one they cannot meaningfully have
     * approved — which is also why the page listing them exists once rather than per path.</p>
     */
    private String offerRenderApproval(LoggedInInfo loggedInInfo, EformContentUnavailableException e,
            String fdid, String demographicNo, EFormRenderApprovalService.Operation operation,
            String message, String approvalAction, String approvalButtonLabel) {
        logger.warn("eForm render incomplete: offering exact-issue approval (operation={} issues={})",
                operation, e.getIssueCount());
        EFormRenderApprovalService approvalService = SpringUtils.getBean(EFormRenderApprovalService.class);
        int requestFdid;
        try {
            requestFdid = Integer.parseInt(fdid);
        } catch (NumberFormatException | NullPointerException parseFailure) {
            setPdfError(PDF_DOWNLOAD_FAILURE_MESSAGE, e);
            return "error";
        }
        String token = approvalService.issue(request, loggedInInfo, requestFdid, demographicNo,
                operation, e.getReport(), null, e.getFdid());
        EFormRenderCompletenessReport report = e.getReport();
        request.setAttribute("renderApproval", token);
        request.setAttribute("fdid", fdid);
        request.setAttribute("demographicNo", demographicNo);
        request.setAttribute("missingContentMessage", message);
        request.setAttribute("approvalAction", approvalAction);
        request.setAttribute("approvalButtonLabel", approvalButtonLabel);
        request.setAttribute("failedContentResources", report.failedContentResources());
        request.setAttribute("excludedContentElements", report.excludedContentElements());
        request.setAttribute("severeConsoleErrors", report.severeConsoleErrors());
        request.setAttribute("containedInteractions", report.containedInteractions());
        request.setAttribute("signatureMissing", report.signatureMissing());
        request.setAttribute("timerCompatibilityFailure", report.timerCompatibilityFailure());
        request.setAttribute("stabilizationCapped", report.stabilizationCapped());
        request.setAttribute("labDecisionSupportStubbed", report.labDecisionSupportStubbed());
        request.setAttribute("providerStampMissing", report.providerStampMissing());
        return "missingContent";
    }

    /** Offers approval for an eForm the completeness gate refused to archive as an eDoc. */
    private String offerEDocApproval(LoggedInInfo loggedInInfo, EformContentUnavailableException e,
            String fdid, String demographicNo) {
        String result = offerRenderApproval(loggedInInfo, e, fdid, demographicNo,
                EFormRenderApprovalService.Operation.EDOC,
                "This eForm could not be fully rendered, so it was not added to the patient's documents."
                        + " Review the omissions below before archiving it.",
                "eform/saveEFormAsEDoc", "Approve listed issues and add to documents");
        return result;
    }

    private void setPdfError(String message, Exception e) {
        logger.error(message, e);
        request.setAttribute(ERROR_ATTRIBUTE, "true");
        request.setAttribute(ERROR_MESSAGE_ATTRIBUTE, message);
    }

    private void setPdfWarning(String message, Exception e) {
        logger.warn(message, e);
        request.setAttribute(WARNING_MESSAGE_ATTRIBUTE, message);
    }

    private String getInvalidFilenameMessage() {
        try {
            return ResourceBundle.getBundle("oscarResources", request.getLocale())
                    .getString(INVALID_FILENAME_MESSAGE_KEY);
        } catch (MissingResourceException e) {
            return "Invalid filename";
        }
    }

    /**
     * Stores email attachment data in session for use after redirect.
     * Session attributes survive redirects, unlike request attributes.
     *
     * <p>All boolean values are pre-validated via {@code "true".equals()} in
     * {@link EmailAttachmentSettings#of}. String values (email fields) are sanitized
     * via {@link EmailAttachmentSettings#of} before storage.</p>
     *
     * @param request HTTP request
     * @param settings EmailAttachmentSettings containing all attachment configuration
     */
    private void addEmailAttachmentsToSession(HttpServletRequest request, EmailAttachmentSettings settings) {
        // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- all values are validated booleans, sanitized strings,
        // or document ID arrays sourced from the eForm save workflow. Output encoding is in EmailCompose2Action.
        HttpSession session = request.getSession();
        session.setAttribute("deleteEFormAfterEmail", settings.deleteEFormAfterEmail()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("isEmailEncrypted", settings.isEmailEncrypted()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("isEmailAttachmentEncrypted", settings.isEmailAttachmentEncrypted()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("isEmailAutoSend", settings.isEmailAutoSend()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("openEFormAfterEmail", settings.openAfterEmail()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("attachEFormItSelf", settings.attachEFormItSelf()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("fdid", settings.fdid()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("demographicId", settings.demographicNo()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("attachedEForms", settings.attachedEForms()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("attachedDocuments", settings.attachedDocuments()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("attachedLabs", settings.attachedLabs()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("attachedHRMDocuments", settings.attachedHRMDocuments()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("attachedForms", settings.attachedForms()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("emailPDFPassword", settings.emailPDFPassword()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- eForm workflow value from EmailAttachmentSettings, not raw request param
        session.setAttribute("emailPDFPasswordClue", settings.emailPDFPasswordClue()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- eForm workflow value from EmailAttachmentSettings, not raw request param
        session.setAttribute("senderEmail", settings.senderEmail()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("subjectEmail", settings.subjectEmail()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("bodyEmail", settings.bodyEmail()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("encryptedMessageEmail", settings.encryptedMessageEmail()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("emailPatientChartOption", settings.emailPatientChartOption()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
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
