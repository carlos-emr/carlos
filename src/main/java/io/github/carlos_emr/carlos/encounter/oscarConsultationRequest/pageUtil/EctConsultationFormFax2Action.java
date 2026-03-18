/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import org.openpdf.text.DocumentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.fax.core.FaxAccount;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action that handles faxing consultation requests to specialists and copy-to recipients.
 *
 * <p>Renders the consultation form (with all attachments) as a PDF via
 * {@link DocumentAttachmentManager}, then creates {@link FaxJob} entries for each recipient.
 * Supports optional cover pages prepended to the fax PDF. Each fax job is persisted and
 * logged for audit purposes.</p>
 *
 * <p>Fax recipients include the primary specialist plus any additional copy-to recipients
 * parsed from JSON-encoded form parameters. The sender's fax line is validated against
 * configured {@link FaxConfig} entries.</p>
 *
 * <p>Requires {@code _con} read privilege via {@link SecurityInfoManager}.</p>
 *
 * @see ConsultationPDFCreator
 * @see DocumentAttachmentManager
 * @see FaxManager
 * @since 2012-04-09
 */
public class EctConsultationFormFax2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
    private final FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private final ClinicDAO clinicDAO = SpringUtils.getBean(ClinicDAO.class);

    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);

    private final NioFileManager nioFileManager = SpringUtils.getBean(NioFileManager.class);

    public EctConsultationFormFax2Action() {
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Renders the consultation form PDF and queues fax jobs for all recipients.
     *
     * <p>Processing flow:</p>
     * <ol>
     *   <li>Validates {@code _con} read privilege</li>
     *   <li>Renders the consultation form with attachments into a PDF</li>
     *   <li>Copies the PDF to the CARLOS documents directory</li>
     *   <li>For each fax recipient: validates the fax number, optionally prepends a cover page,
     *       creates and persists a {@link FaxJob}, and logs the transaction</li>
     * </ol>
     *
     * @return String "success" on successful fax queuing, "cancel" if cancelled,
     *         "error" on failure, or null on unexpected error
     */
    @Override
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        //EctConsultationFaxForm ectConsultationFaxForm = (EctConsultationFaxForm) form;

        if ("cancel".equals(this.getMethod())) {
            return "cancel";
        }

    	this.setRequest(request);
	   	String reqId = this.getRequestId();
		String demoNo = this.getDemographicNo();
		String faxNumber = this.getSenderFaxNumber();
		String consultResponsePage = request.getParameter("consultResponsePage");
		boolean doCoverPage = this.isCoverpage();
		String note = "";
		if ( doCoverPage ) {
			note = request.getParameter("note") == null ? "" : request.getParameter("note");
			// dont ask!
			if (note.isEmpty()) {
				note = this.getComments();
			}
		}
		FaxAccount sender = this.getSender();
		Clinic clinic = clinicDAO.getClinic();
		sender.setSubText(clinic.getClinicName());
		sender.setAddress(clinic.getClinicAddress());
		sender.setFacilityName(clinic.getClinicName());

        /*
         * This is a temporary solution until the fax code is refactored and added to their respective manager classes.
         */
        String provider_no = loggedInInfo.getLoggedInProviderNo();
        String error = "";
        Exception exception = null;

        request.setAttribute("reqId", reqId);
        request.setAttribute("demographicId", demoNo);
        Path faxPdf = null;
        try {
            faxPdf = documentAttachmentManager.renderConsultationFormWithAttachments(request, response);
        } catch (PDFGenerationException e) {
            logger.error(e.getMessage(), e);
            String errorMessage = "This fax could not be sent. \\n\\n" + e.getMessage();
            request.setAttribute("errorMessage", errorMessage);
            return "error";
        }
        String faxPdfPath = nioFileManager.copyFileToOscarDocuments(faxPdf.toString());
        faxPdf = Paths.get(faxPdfPath);
        Path pdfToFax;
        List<FaxConfig> faxConfigs = faxConfigDao.findAll(null, null);
        boolean validFaxNumber;
        int count = 0;
        Set<FaxRecipient> faxRecipients = this.getAllFaxRecipients();
        try {
            for (FaxRecipient faxRecipient : faxRecipients) {

                // reset target pdf.
                pdfToFax = faxPdf;

                String faxNo = faxRecipient.getFax();

                if (faxNo == null) {
                    faxNo = "";
                }

                if (faxNo.length() < 7) {
                    throw new DocumentException("Document target fax number '" + faxNo + "' is invalid.");
                }

                faxNo = faxNo.trim().replaceAll("\\D", "");

                logger.info("Setting up fax to: " + faxRecipient.getName() + " at " + faxRecipient.getFax());

                validFaxNumber = false;

                FaxJob faxJob = new FaxJob();
                faxJob.setDestination(faxNo);
                faxJob.setRecipient(faxRecipient.getName());
                faxJob.setFax_line(faxNumber);
                faxJob.setStamp(new Date());
                faxJob.setOscarUser(provider_no);
                faxJob.setDemographicNo(Integer.parseInt(demoNo));

                inner:
                for (FaxConfig faxConfig : faxConfigs) {
                    if (faxConfig.getFaxNumber().equals(faxNumber)) {

                        faxJob.setStatus(FaxJob.STATUS.WAITING);
                        faxJob.setUser(faxConfig.getFaxUser());
                        sender.setFaxNumberOwner(faxConfig.getAccountName());
                        validFaxNumber = true;
                        break inner;
                    }
                }

                if (!validFaxNumber) {

                    faxJob.setStatus(FaxJob.STATUS.ERROR);
                    faxJob.setStatusString("Document outgoing fax number '" + faxNumber + "' is invalid.");
                    logger.error("PROBLEM CREATING FAX JOB", new DocumentException("Document outgoing fax number '" + faxNumber + "' is invalid."));
                } else {
                    // redundant, but, what the heck!
                    faxJob.setStatus(FaxJob.STATUS.WAITING);
                }

                //todo rethink this process.  It takes up too much disc space.
                if (doCoverPage) {
                    pdfToFax = faxManager.addCoverPage(loggedInInfo, note, faxRecipient, sender, faxPdf);

                    // delete the source file to save some disc space
                    if (count == (faxRecipients.size() - 1)) {
                        Files.deleteIfExists(faxPdf);
                    }
                }

                int numPages = EDocUtil.getPDFPageCount(pdfToFax.toString());

                faxJob.setFile_name(pdfToFax.getFileName().toString());
                faxJob.setNumPages(numPages);

                faxJobDao.persist(faxJob);

                // start up a log track each time the CLIENT was run.
                faxManager.logFaxJob(loggedInInfo, faxJob, TransactionType.CONSULTATION, Integer.parseInt(reqId));
                // FaxClientLog faxClientLog = new FaxClientLog();
                // faxClientLog.setFaxId(faxJob.getId()); // IMPORTANT! this is the id of the FaxJobID from the Faxes table. A 1:1 cardinality.
                // faxClientLog.setProviderNo(faxJob.getOscarUser()); // the providers that sent this fax
                // faxClientLog.setStartTime(new Date(System.currentTimeMillis())); // the exact time the fax was sent
                // faxClientLog.setRequestId(Integer.parseInt(reqId));
                // faxClientLogDao.persist(faxClientLog);

                count++;
            }

            LogAction.addLog(provider_no, LogConst.SENT, LogConst.CON_FAX, "CONSULT " + reqId);
            request.setAttribute("faxSuccessful", true);
            return SUCCESS;
        } catch (DocumentException de) {
            error = "DocumentException";
            exception = de;
        } catch (IOException ioe) {
            error = "IOException";
            exception = ioe;
        }
        if (!error.equals("")) {
            logger.error(error + " occured insided ConsultationPrintAction", exception);
            request.setAttribute("printError", Boolean.valueOf(true));
            return "error";
        }
        return null;
    }


    private String method;
    private String recipient;
    private String from;
    private String recipientFaxNumber;
    private String sendersPhone;
    private String sendersFax;
    private String senderFaxNumber;
    private String comments;
    private String requestId;
    private String transType;
    private String demographicNo;
    private String[] faxRecipients;
    private boolean coverpage;
    private Set<FaxRecipient> allFaxRecipients;
    private Set<FaxRecipient> copiedTo;
    private FaxAccount sender;

    public String getMethod() {
        return method;
    }
    @StrutsParameter
    public void setMethod(String method) {
        this.method = method;
    }
    public String getRecipient() {
        return recipient;
    }
    @StrutsParameter
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    public String getFrom() {
        return from;
    }
    @StrutsParameter
    public void setFrom(String from) {
        this.from = from;
    }
    public String getRecipientFaxNumber() {
        if (recipientFaxNumber != null) {
            recipientFaxNumber = recipientFaxNumber.trim().replaceAll("\\D", "");
        }
        return recipientFaxNumber;
    }
    @StrutsParameter
    public void setRecipientFaxNumber(String recipientFaxNumber) {
        this.recipientFaxNumber = recipientFaxNumber;
    }
    public String getSendersPhone() {
        return sendersPhone;
    }
    @StrutsParameter
    public void setSendersPhone(String sendersPhone) {
        this.sendersPhone = sendersPhone;
    }
    public String getSendersFax() {
        return sendersFax;
    }
    @StrutsParameter
    public void setSendersFax(String sendersFax) {
        this.sendersFax = sendersFax;
    }

    public String getSenderFaxNumber() {
        return senderFaxNumber;
    }

    @StrutsParameter
    public void setSenderFaxNumber(String senderFaxNumber) {
        this.senderFaxNumber = senderFaxNumber;
    }

    public String getComments() {
        return comments;
    }
    @StrutsParameter
    public void setComments(String comments) {
        this.comments = comments;
    }
    public String getRequestId() {
        return requestId;
    }
    @StrutsParameter
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    public String getTransType() {
        return transType;
    }
    @StrutsParameter
    public void setTransType(String transType) {
        this.transType = transType;
    }
    public String getDemographicNo() {
        return demographicNo;
    }
    @StrutsParameter
    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }
    public String[] getFaxRecipients() {
        if (faxRecipients ==  null) {
            return new String[]{};
        }
        return faxRecipients;
    }
    @StrutsParameter
    public void setFaxRecipients(String[] faxRecipients) {
        this.faxRecipients = faxRecipients;
    }
    public boolean isCoverpage() {
        return coverpage;
    }
    @StrutsParameter
    public void setCoverpage(boolean coverpage) {
        this.coverpage = coverpage;
    }
    /**
     * Returns all fax recipients, including the primary recipient and any copy-to entries.
     *
     * <p>Lazily initialized on first access. Combines the primary recipient (from
     * {@link #getRecipient()} and {@link #getRecipientFaxNumber()}) with all copy-to
     * recipients from {@link #getCopiedTo()}.</p>
     *
     * @return Set of FaxRecipient all fax recipients for this consultation
     */
    public Set<FaxRecipient> getAllFaxRecipients() {
        if (allFaxRecipients == null) {
            allFaxRecipients = new HashSet<FaxRecipient>();
            allFaxRecipients.add( new FaxRecipient( getRecipient(), getRecipientFaxNumber() ) );
            allFaxRecipients.addAll(getCopiedTo());
        }

        return allFaxRecipients;
    }

    /**
     * Parses and returns the copy-to fax recipients from the JSON-encoded form parameters.
     *
     * <p>Each entry in the {@code faxRecipients} array is a JSON fragment containing
     * recipient name and fax number, parsed into {@link FaxRecipient} objects.</p>
     *
     * @return Set of FaxRecipient the copy-to recipients (empty set if none)
     */
    public Set<FaxRecipient> getCopiedTo() {
        if (copiedTo == null) {
            copiedTo = new HashSet<FaxRecipient>();
            for (String faxRecipient : getFaxRecipients()) {
                try {
                    ObjectNode jsonObject = (ObjectNode) objectMapper.readTree("{" + faxRecipient + "}");
                    copiedTo.add(new FaxRecipient(jsonObject));
                }
                catch (Exception e) {
                    logger.error("Error parsing copied to fax recipient: " + faxRecipient, e);
                }
            }
        }
        return copiedTo;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * Builds and returns the sender's fax account details from form parameters.
     *
     * @return FaxAccount the sender account with fax number, letterhead name, and phone
     */
    public FaxAccount getSender() {
        if (sender == null) {
            sender = new FaxAccount();
        }

        sender.setFax(getSenderFaxNumber());
        sender.setLetterheadName(getFrom());
        sender.setPhone(getSendersPhone());

        return sender;
    }

}
