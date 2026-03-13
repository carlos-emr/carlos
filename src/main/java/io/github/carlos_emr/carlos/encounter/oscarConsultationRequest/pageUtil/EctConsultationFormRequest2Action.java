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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.utility.*;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v26.message.ORU_R01;
import ca.uhn.hl7v2.model.v26.message.REF_I12;
import com.opensymphony.xwork2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar.OruR01;
import io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar.OruR01.ObservationData;
import io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar.RefI12;
import io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar.SendingUtils;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import org.openpdf.text.DocumentException;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Struts2 action that handles creating, updating, printing, faxing, and electronically
 * sending consultation requests (clinical referrals).
 *
 * <p>This is the primary controller for the consultation request form. It processes form
 * submissions based on the {@code submission} parameter prefix:</p>
 * <ul>
 *   <li><b>"Submit..."</b> - creates a new {@link ConsultationRequest}, persists it with all
 *       attached documents (labs, forms, eForms, HRM reports), and processes digital signatures</li>
 *   <li><b>"Update..."</b> - archives the existing consultation request, updates it with new
 *       form data, and re-attaches documents</li>
 *   <li><b>"...And Print Preview"</b> - renders the consultation form with attachments as a
 *       base64-encoded PDF for inline preview</li>
 *   <li><b>"...And Fax"</b> - prepares fax parameters (accounts, recipients, attached document
 *       descriptions) and forwards to the fax confirmation page</li>
 *   <li><b>"...esend"</b> - transmits the consultation via HL7 REF_I12 message to the
 *       specialist's remote EMR system, with attached documents sent as ORU_R01 messages</li>
 * </ul>
 *
 * <p>Supports the Health Care Team integration when the
 * {@code ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS} property is enabled,
 * bridging between the newer DemographicContact model and the legacy ProfessionalSpecialist
 * module for backward compatibility.</p>
 *
 * <p>Requires {@code _con} write privilege via {@link SecurityInfoManager}.</p>
 *
 * @see ConsultationPDFCreator
 * @see DocumentAttachmentManager
 * @see ConsultationManager
 * @since 2003-07-21
 */
public class EctConsultationFormRequest2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private ConsultationManager consultationManager = SpringUtils.getBean(ConsultationManager.class);
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private FaxManager faxManager = SpringUtils.getBean(FaxManager.class);

    private final DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Processes the consultation request form submission.
     *
     * <p>Dispatches to create, update, print preview, fax, or HL7 e-send based on the
     * {@code submission} parameter. All paths enforce {@code _con} write privilege.</p>
     *
     * @return String the Struts2 result name ("success", "fax", "error", or NONE for redirects)
     * @throws ServletException when form rendering encounters a servlet error
     * @throws IOException      when an I/O error occurs during response writing or HL7 sending
     */
    @Override
    public String execute() throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "w", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String appointmentHour = this.getAppointmentHour();
        String appointmentPm = this.getAppointmentPm();
        String[] attachedDocuments = this.getDocNo();
        String[] attachedLabs = this.getLabNo();
        String[] attachedForms = this.getFormNo();
        String[] attachedEForms = this.geteFormNo();
        String[] attachedHRMDocuments = this.getHrmNo();
        List<String> documents = new ArrayList<String>();

        if (appointmentPm.equals("PM") && Integer.parseInt(appointmentHour) < 12) {
            appointmentHour = Integer.toString(Integer.parseInt(appointmentHour) + 12);
        } else if (appointmentHour.equals("12") && appointmentPm.equals("AM")) {
            appointmentHour = "0";
        }

        String sendTo = this.getSendTo();
        String submission = this.getSubmission();
        String providerNo = this.getProviderNo();
        String demographicNo = this.getDemographicNo();

        String requestId = "";

        boolean newSignature = request.getParameter("newSignature") != null && request.getParameter("newSignature").equalsIgnoreCase("true");
        String signatureId = null;
        String signatureImg = this.getSignatureImg();
        if (StringUtils.isBlank(signatureImg)) {
            signatureImg = request.getParameter("newSignatureImg");
            if (signatureImg == null) {
                signatureImg = "";
            }
        }

        ConsultationRequestDao consultationRequestDao = (ConsultationRequestDao) SpringUtils.getBean(ConsultationRequestDao.class);
        ConsultationRequestExtDao consultationRequestExtDao = (ConsultationRequestExtDao) SpringUtils.getBean(ConsultationRequestExtDao.class);
        ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

        String[] format = new String[]{"yyyy-MM-dd", "yyyy/MM/dd"};

        if (submission.startsWith("Submit")) {

            try {
                int demographicId;
                try {
                    demographicId = Integer.parseInt(demographicNo);
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().error("Invalid demographic number for new consultation: {}", demographicNo);
                    addActionError("Invalid demographic number");
                    return INPUT;
                }

                if (newSignature) {
                    // Manual signature from tablet/signature pad
                    DigitalSignature signature = digitalSignatureManager.processAndSaveDigitalSignature(loggedInInfo, signatureImg, demographicId, ModuleType.CONSULTATION);
                    if (signature != null) {
                        signatureId = "" + signature.getId();
                    }
                } else {
                    // Stamp signature — attempt to create immutable copy from provider's stamp file
                    // (returns null if digital signatures are disabled, stamp file is missing, or an error occurs)
                    DigitalSignature signature = digitalSignatureManager.saveStampSignature(
                            loggedInInfo, loggedInInfo.getLoggedInProviderNo(), demographicId, ModuleType.CONSULTATION);
                    if (signature != null) {
                        signatureId = "" + signature.getId();
                    } else {
                        MiscUtils.getLogger().debug("Stamp signature could not be applied for provider {} on new consultation", loggedInInfo.getLoggedInProviderNo());
                    }
                }


                ConsultationRequest consult = new ConsultationRequest();
                String dateString = this.getReferalDate();
                Date date = null;
                if (dateString != null && !dateString.isEmpty()) {
                    date = DateUtils.parseDate(dateString, format);
                }
                consult.setReferralDate(date);
                consult.setServiceId(Integer.valueOf(this.getService()));

                consult.setSignatureImg(signatureId);

                consult.setLetterheadName(this.getLetterheadName());
                consult.setLetterheadAddress(this.getLetterheadAddress());
                consult.setLetterheadPhone(this.getLetterheadPhone());
                consult.setLetterheadFax(this.getLetterheadFax());

                if (this.getAppointmentDate() != null && !this.getAppointmentDate().equals("")) {
                    date = DateUtils.parseDate(this.getAppointmentDate(), format);
                    consult.setAppointmentDate(date);

                    if (!StringUtils.isEmpty(appointmentHour) && !StringUtils.isEmpty(this.getAppointmentMinute())) {
                        try {
                            date = DateUtils.setHours(date, Integer.valueOf(appointmentHour));
                            date = DateUtils.setMinutes(date, Integer.valueOf(this.getAppointmentMinute()));
                            consult.setAppointmentTime(date);
                        } catch (NumberFormatException nfEx) {
                            MiscUtils.getLogger().error("Invalid Time", nfEx);
                        }
                    }
                } else {
                    consult.setAppointmentDate(null);
                    consult.setAppointmentTime(null);
                }
                consult.setReasonForReferral(this.getReasonForConsultation());
                consult.setClinicalInfo(this.getClinicalInformation());
                consult.setCurrentMeds(this.getCurrentMedications());
                consult.setAllergies(this.getAllergies());
                consult.setProviderNo(this.getProviderNo());
                consult.setDemographicId(Integer.valueOf(this.getDemographicNo()));
                consult.setStatus(this.getStatus());
                consult.setStatusText(this.getAppointmentNotes());
                consult.setSendTo(this.getSendTo());
                consult.setConcurrentProblems(this.getConcurrentProblems());
                consult.setUrgency(this.getUrgency());
                consult.setAppointmentInstructions(this.getAppointmentInstructions());
                consult.setSiteName(this.getSiteName());
                Boolean pWillBook = false;
                if (this.getPatientWillBook() != null) {
                    pWillBook = this.getPatientWillBook().equals("1");
                }
                consult.setPatientWillBook(pWillBook);

                if (this.getFollowUpDate() != null && !this.getFollowUpDate().equals("")) {
                    date = DateUtils.parseDate(this.getFollowUpDate(), format);
                    consult.setFollowUpDate(date);
                }

                Integer specId = null;

                if (!this.getSpecialist().isEmpty()) {
                    specId = Integer.parseInt(this.getSpecialist());
                }

                // converting the newer Contacts Table and Health Care Team back and forth
                // from the older professionalSpecialist module.
                // This should persist and retrieve values to be backwards compatible.
                if (OscarProperties.getInstance().getBooleanProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "true")) {

                    // when this is enabled the demographicContactId is being posted as a specId variable.
                    Integer demographicContactId = Integer.valueOf(specId);

                    // specId is reset to unknown.
                    specId = 0;

                    DemographicContact demographicContact = demographicManager.getHealthCareMemberbyId(loggedInInfo, demographicContactId);

                    if (demographicContact != null) {

                        consult.setDemographicContact(demographicContact);

                        // If the demographicContact is holding the specId, then retrieve it for backwards
                        // compatibility. For the most part only contacts in the professionalSpecialist table should get through the
                        // filters.
                        if (DemographicContact.TYPE_PROFESSIONALSPECIALIST == demographicContact.getType()) {
                            specId = Integer.parseInt(demographicContact.getContactId());
                        }
                    }
                }

                // only add the professionalSpecialist if it checks out. 0 will obviously return a null.
                ProfessionalSpecialist professionalSpecialist = professionalSpecialistDao.find(specId);

                if (professionalSpecialist != null) {
                    request.setAttribute("professionalSpecialistName", professionalSpecialist.getFormattedTitle());
                    consult.setProfessionalSpecialist(professionalSpecialist);
                }

                consultationRequestDao.persist(consult);

                requestId = String.valueOf(consult.getId());
                MiscUtils.getLogger().debug("saved new consult id " + requestId);

                Enumeration e = request.getParameterNames();
                while (e.hasMoreElements()) {
                    String name = (String) e.nextElement();
                    if (name.startsWith("ext_")) {
                        String value = request.getParameter(name);
                        consultationRequestExtDao.persist(createExtEntry(requestId, name.substring(name.indexOf("_") + 1), value));
                    }
                }

                // now that we have consultation id we can save any attached docs as well
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.DOC, attachedDocuments, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.LAB, attachedLabs, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.FORM, attachedForms, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.EFORM, attachedEForms, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.HRM, attachedHRMDocuments, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
            } catch (ParseException e) {
                MiscUtils.getLogger().error("Invalid Date", e);
            }
            request.setAttribute("reqId", requestId);
            request.setAttribute("transType", "2");

        } else if (submission.startsWith("Update")) {
            requestId = this.getRequestId();
            consultationManager.archiveConsultationRequest(Integer.parseInt(requestId));

            try {
                int demographicId;
                try {
                    demographicId = Integer.parseInt(demographicNo);
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().error("Invalid demographic number for consultation update: {}", demographicNo);
                    addActionError("Invalid demographic number");
                    return INPUT;
                }

                if (newSignature) {
                    // Manual re-sign from tablet/signature pad
                    DigitalSignature signature = digitalSignatureManager.processAndSaveDigitalSignature(loggedInInfo, signatureImg, demographicId, ModuleType.CONSULTATION);
                    if (signature != null) {
                        signatureId = "" + signature.getId();
                    } else {
                        signatureId = null;
                    }
                } else if (signatureImg == null || signatureImg.isEmpty()) {
                    // Stamp signature with no existing DigitalSignature — attempt to create immutable copy
                    // (returns null if digital signatures are disabled, stamp file is missing, or an error occurs)
                    DigitalSignature signature = digitalSignatureManager.saveStampSignature(
                            loggedInInfo, loggedInInfo.getLoggedInProviderNo(), demographicId, ModuleType.CONSULTATION);
                    if (signature != null) {
                        signatureId = "" + signature.getId();
                    } else {
                        MiscUtils.getLogger().debug("Stamp signature could not be applied for provider {} on consultation update (requestId={})", loggedInInfo.getLoggedInProviderNo(), requestId);
                    }
                } else {
                    // Already has a DigitalSignature ID — keep it
                    signatureId = signatureImg;
                }

                ConsultationRequest consult = consultationRequestDao.find(Integer.valueOf(requestId));
                Date date = null;

                // By default, the referral date will not have a value on edit, so we need to make sure
                // that the newly inputted date is parsed, or pulled from the previous date value
                if (StringUtils.isNotBlank(this.getReferalDate())) {
                    date = DateUtils.parseDate(this.getReferalDate(), format);
                } else {
                    date = consult.getReferralDate();
                }

                consult.setReferralDate(date);
                consult.setServiceId(Integer.valueOf(this.getService()));
                consult.setSignatureImg(signatureId);
                consult.setProviderNo(this.getProviderNo());
                consult.setLetterheadName(this.getLetterheadName());
                consult.setLetterheadAddress(this.getLetterheadAddress());
                consult.setLetterheadPhone(this.getLetterheadPhone());
                consult.setLetterheadFax(this.getLetterheadFax());

                Integer specId = null;
                if (!this.getSpecialist().isEmpty()) {
                    specId = Integer.valueOf(this.getSpecialist());
                }

                // converting the newer Contacts Table and Health Care Team back and forth
                // from the older professionalSpecialist module.
                // This should persist and retrieve values to be backwards compatible.
                if (OscarProperties.getInstance().getBooleanProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "true")) {
                    DemographicContact demographicContact = demographicManager.getHealthCareMemberbyId(loggedInInfo, specId);
                    if (demographicContact != null) {
                        consult.setDemographicContact(demographicContact);

                        // add in the professional specialist to enable backwards compatibility.
                        if (DemographicContact.TYPE_PROFESSIONALSPECIALIST == demographicContact.getType()) {
                            specId = Integer.parseInt(demographicContact.getContactId());
                        }
                    }
                }

                // only add the professionalSpecialist if it checks out.
                ProfessionalSpecialist professionalSpecialist = new ProfessionalSpecialist();
                if (specId != null) {
                    professionalSpecialist = professionalSpecialistDao.find(specId);
                }

                if (professionalSpecialist != null) {
                    request.setAttribute("professionalSpecialistName", professionalSpecialist.getFormattedTitle());
                    consult.setProfessionalSpecialist(professionalSpecialist);
                }


                if (this.getAppointmentDate() != null && !this.getAppointmentDate().equals("")) {
                    date = DateUtils.parseDate(this.getAppointmentDate(), format);
                    consult.setAppointmentDate(date);
                    try {
                        date = DateUtils.setHours(date, Integer.valueOf(appointmentHour));
                        date = DateUtils.setMinutes(date, Integer.valueOf(this.getAppointmentMinute()));
                        consult.setAppointmentTime(date);
                    } catch (NumberFormatException nfEx) {
                        MiscUtils.getLogger().error("Invalid Time", nfEx);
                    }
                } else {
                    consult.setAppointmentDate(null);
                    consult.setAppointmentTime(null);
                }
                consult.setReasonForReferral(this.getReasonForConsultation());
                consult.setClinicalInfo(this.getClinicalInformation());
                consult.setCurrentMeds(this.getCurrentMedications());
                consult.setAllergies(this.getAllergies());
                consult.setDemographicId(Integer.valueOf(this.getDemographicNo()));
                consult.setStatus(this.getStatus());
                consult.setStatusText(this.getAppointmentNotes());
                consult.setSendTo(this.getSendTo());
                consult.setConcurrentProblems(this.getConcurrentProblems());
                consult.setUrgency(this.getUrgency());
                consult.setAppointmentInstructions(this.getAppointmentInstructions());
                consult.setSiteName(this.getSiteName());
                Boolean pWillBook = false;
                if (this.getPatientWillBook() != null) {
                    pWillBook = this.getPatientWillBook().equals("1");
                }
                consult.setPatientWillBook(pWillBook);

                if (this.getFollowUpDate() != null && !this.getFollowUpDate().equals("")) {
                    date = DateUtils.parseDate(this.getFollowUpDate(), format);
                    consult.setFollowUpDate(date);
                }
                consultationRequestDao.merge(consult);

                consultationRequestExtDao.clear(Integer.parseInt(requestId));
                Enumeration e = request.getParameterNames();
                while (e.hasMoreElements()) {
                    String name = (String) e.nextElement();
                    if (name.startsWith("ext_")) {
                        String value = request.getParameter(name);
                        consultationRequestExtDao.persist(createExtEntry(requestId, name.substring(name.indexOf("_") + 1), value));
                    }
                }

                // save any additional attachments added on the update
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.DOC, attachedDocuments, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.LAB, attachedLabs, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.FORM, attachedForms, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.EFORM, attachedEForms, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
                documentAttachmentManager.attachToConsult(loggedInInfo, DocumentType.HRM, attachedHRMDocuments, providerNo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));
            } catch (ParseException e) {
                MiscUtils.getLogger().error("Error", e);
            }

            request.setAttribute("transType", "1");

        } else if (submission.equalsIgnoreCase("And Print Preview")) {
            renderConsultationFormWithAttachments(request, response, this.getRequestId(), demographicNo);
            generatePDFResponse(request, response);
            return null;
        }


        this.setRequestId("");

        request.setAttribute("teamVar", sendTo);

        if (submission.endsWith("And Print Preview")) {
            if (renderConsultationFormWithAttachments(request, response, requestId, demographicNo)) {
                return SUCCESS;
            }
            return "error";
        } else if (submission.endsWith("And Fax")) {

            String[] faxRecipients = request.getParameterValues("faxRecipients");
            HashSet<FaxRecipient> copytoRecipients = new HashSet<FaxRecipient>();

            if (faxRecipients != null) {
                for (String recipient : faxRecipients) {
                    ObjectNode jsonObject = (ObjectNode) objectMapper.readTree(recipient);
                    String fax = jsonObject.get("fax").asText();
                    String name = jsonObject.get("name").asText();
                    copytoRecipients.add(new FaxRecipient(name, fax));
                }
            }


            // call-back document descriptions into documents parameter.
            List<EDoc> attachedDocumentList = EDocUtil.listDocs(loggedInInfo, demographicNo, requestId, EDocUtil.ATTACHED);
            CommonLabResultData commonLabResultData = new CommonLabResultData();
            List<LabResultData> attachedLabList = commonLabResultData.populateLabResultsData(loggedInInfo, demographicNo, requestId, CommonLabResultData.ATTACHED);

            List<EctFormData.PatientForm> attachedFormsList = consultationManager.getAttachedForms(loggedInInfo, Integer.parseInt(requestId), Integer.parseInt(demographicNo));

            List<EFormData> attachedEFormsList = consultationManager.getAttachedEForms(requestId);

            ArrayList<HashMap<String, ? extends Object>> attachedHRMDocumentsList = consultationManager.getAttachedHRMDocuments(loggedInInfo, demographicNo, requestId);

            if (attachedDocumentList != null) {
                for (EDoc documentItem : attachedDocumentList) {
                    String description = documentItem.getDescription();
                    if (description == null || description == "") {
                        description = documentItem.getFileName();
                    }
                    documents.add(description);
                }
            }

            if (attachedLabList != null) {
                for (LabResultData labResultData : attachedLabList) {
                    documents.add(labResultData.getDisciplineDisplayString());
                }
            }

            if (attachedFormsList != null && !attachedFormsList.isEmpty()) {
                for (EctFormData.PatientForm attachedForm : attachedFormsList) {
                    documents.add(attachedForm.formName);
                }
            }

            if (attachedEFormsList != null && !attachedEFormsList.isEmpty()) {
                for (EFormData attachedEForm : attachedEFormsList) {
                    documents.add(attachedEForm.getFormName());
                }
            }

            if (attachedHRMDocumentsList != null && !attachedHRMDocumentsList.isEmpty()) {
                for (HashMap<String, ? extends Object> attachedHRMDocument : attachedHRMDocumentsList) {
                    documents.add(attachedHRMDocument.get("name") + "");
                }
            }

			List<FaxConfig>	accounts = faxManager.getFaxGatewayAccounts(loggedInInfo);

			// fax number that will display on the letterhead
	        request.setAttribute("letterheadFax", this.getLetterheadFax());
			// fax account that will be used to send the fax
			request.setAttribute("faxAccount", this.getFaxAccount());
		  	request.setAttribute("documents", documents);			
			request.setAttribute("copyToRecipients", copytoRecipients);
			request.setAttribute("reqId", requestId);
			request.setAttribute("accounts", accounts);
			request.setAttribute("transactionType", TransactionType.CONSULTATION.name());
			request.setAttribute("transType", "consultRequest");
			
			return "fax";
			
		} 
		else if (submission.endsWith("esend"))
		{
			// upon success continue as normal with success message
			// upon failure, go to consultation update page with message
			try {
	            doHl7Send(loggedInInfo, Integer.parseInt(requestId));
	            WebUtils.addLocalisedInfoMessage(request, "oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgCreatedUpdateESent");
            } catch (Exception e) {
                logger.error("Error contacting remote server.", e);

                WebUtils.addLocalisedErrorMessage(request, "oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgCreatedUpdateESendError");
                String forward = "/oscarEncounter/oscarConsultationRequest/ConsultationFormRequest.jsp" + "?de=" + demographicNo +
                        "&requestId=" + requestId;
                response.sendRedirect(forward);
                return NONE;
            }
        }

        String contextPath = request.getContextPath();
        String forward = contextPath + "/oscarEncounter/oscarConsultationRequest/ConfirmConsultationRequest.jsp?de=" + demographicNo;
        response.sendRedirect(forward);
        return NONE;
    }

    /**
     * Creates a consultation request extension entry for storing custom form fields.
     *
     * <p>Extension entries store additional key-value data beyond the core consultation
     * request fields, identified by the {@code ext_} parameter name prefix.</p>
     *
     * @param requestId String the consultation request ID
     * @param name      String the extension key (parameter name after "ext_" prefix)
     * @param value     String the extension value
     * @return ConsultationRequestExt the new extension entry ready for persistence
     */
    private ConsultationRequestExt createExtEntry(String requestId, String name, String value) {
        ConsultationRequestExt obj = new ConsultationRequestExt();
        obj.setDateCreated(new Date());
        obj.setKey(name);
        obj.setValue(value);
        obj.setRequestId(Integer.parseInt(requestId));
        return obj;
    }

    /**
     * Sends the consultation request and its attachments to the specialist's remote EMR via HL7.
     *
     * <p>Constructs an HL7 REF_I12 referral message for the consultation request and sends
     * it to the specialist's remote endpoint. Then iterates over attached documents and lab
     * results, wrapping each as an ORU_R01 observation message with the binary data payload,
     * and sends them individually.</p>
     *
     * @param loggedInInfo          LoggedInInfo the authenticated session context
     * @param consultationRequestId Integer the consultation request ID to send
     * @throws InvalidKeyException      when cryptographic key validation fails
     * @throws SignatureException        when digital signature operation fails
     * @throws NoSuchAlgorithmException  when a required cryptographic algorithm is unavailable
     * @throws NoSuchPaddingException    when a required padding scheme is unavailable
     * @throws IllegalBlockSizeException when block cipher input size is incorrect
     * @throws BadPaddingException       when decrypted data padding is incorrect
     * @throws InvalidKeySpecException   when key specification is invalid
     * @throws IOException               when an I/O error occurs during transmission
     * @throws HL7Exception              when HL7 message construction or parsing fails
     * @throws ServletException          when the remote server returns a non-OK status
     */
    private void doHl7Send(LoggedInInfo loggedInInfo, Integer consultationRequestId) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, IOException, HL7Exception, ServletException {

        ConsultationRequestDao consultationRequestDao = (ConsultationRequestDao) SpringUtils.getBean(ConsultationRequestDao.class);
        ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
        Hl7TextInfoDao hl7TextInfoDao = (Hl7TextInfoDao) SpringUtils.getBean(Hl7TextInfoDao.class);
        ClinicDAO clinicDAO = (ClinicDAO) SpringUtils.getBean(ClinicDAO.class);

        ConsultationRequest consultationRequest = consultationRequestDao.find(consultationRequestId);
        ProfessionalSpecialist professionalSpecialist = professionalSpecialistDao.find(consultationRequest.getSpecialistId());
        Clinic clinic = clinicDAO.getClinic();

        // set status now so the remote version shows this status
        consultationRequest.setStatus("2");

        REF_I12 refI12 = RefI12.makeRefI12(clinic, consultationRequest);
        SendingUtils.send(loggedInInfo, refI12, professionalSpecialist);

        // save after the sending just in case the sending fails.
        consultationRequestDao.merge(consultationRequest);

        //--- send attachments ---
        Provider sendingProvider = loggedInInfo.getLoggedInProvider();
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, consultationRequest.getDemographicId());

        //--- process all documents ---
        ArrayList<EDoc> attachments = EDocUtil.listDocs(loggedInInfo, demographic.getDemographicNo().toString(), consultationRequest.getId().toString(), EDocUtil.ATTACHED);
        for (EDoc attachment : attachments) {
            ObservationData observationData = new ObservationData();
            observationData.subject = attachment.getDescription();
            observationData.textMessage = "Attachment for consultation : " + consultationRequestId;
            observationData.binaryDataFileName = attachment.getFileName();
            observationData.binaryData = attachment.getFileBytes();

            ORU_R01 hl7Message = OruR01.makeOruR01(clinic, demographic, observationData, sendingProvider, professionalSpecialist);
            SendingUtils.send(loggedInInfo, hl7Message, professionalSpecialist);
        }

        //--- process all labs ---
        CommonLabResultData labData = new CommonLabResultData();
        ArrayList<LabResultData> labs = labData.populateLabResultsData(loggedInInfo, demographic.getDemographicNo().toString(), consultationRequest.getId().toString(), CommonLabResultData.ATTACHED);
        for (LabResultData attachment : labs) {
            try {
                byte[] dataBytes = LabPDFCreator.getPdfBytes(attachment.getSegmentID(), sendingProvider.getProviderNo());
                Hl7TextInfo hl7TextInfo = hl7TextInfoDao.findLabId(Integer.parseInt(attachment.getSegmentID()));

                ObservationData observationData = new ObservationData();
                observationData.subject = hl7TextInfo.getDiscipline();
                observationData.textMessage = "Attachment for consultation : " + consultationRequestId;
                observationData.binaryDataFileName = hl7TextInfo.getDiscipline() + ".pdf";
                observationData.binaryData = dataBytes;


                ORU_R01 hl7Message = OruR01.makeOruR01(clinic, demographic, observationData, sendingProvider, professionalSpecialist);
                int statusCode = SendingUtils.send(loggedInInfo, hl7Message, professionalSpecialist);
                if (HttpServletResponse.SC_OK != statusCode)
                    throw (new ServletException("Error, received status code:" + statusCode));
            } catch (DocumentException e) {
                logger.error("Unexpected error.", e);
            }
        }
    }

    /**
     * Renders the consultation form with all attachments as a base64-encoded PDF.
     *
     * <p>Sets the generated PDF and a suggested filename as request attributes for
     * the print preview page. Returns false and sets an error message attribute
     * if PDF generation fails.</p>
     *
     * @param request       HttpServletRequest the current request
     * @param response      HttpServletResponse the current response
     * @param requestId     String the consultation request ID
     * @param demographicNo String the patient demographic number
     * @return boolean true if PDF generation succeeded, false on error
     */
    private boolean renderConsultationFormWithAttachments(HttpServletRequest request, HttpServletResponse response, String requestId, String demographicNo) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        request.setAttribute("reqId", requestId);
        request.setAttribute("demographicId", demographicNo);
        String fileName = generateFileName(loggedInInfo, Integer.parseInt(demographicNo));
        String base64PDF = "";
        try {
            Path pdfPath = documentAttachmentManager.renderConsultationFormWithAttachments(request, response);
            base64PDF = documentAttachmentManager.convertPDFToBase64(pdfPath);
        } catch (PDFGenerationException e) {
            logger.error(e.getMessage(), e);
            String errorMessage = "A print preview of this consultation could not be generated. \\n\\n" + e.getMessage();
            request.setAttribute("errorMessage", errorMessage);
            return false;
        }

        request.setAttribute("consultPDFName", fileName);
        request.setAttribute("consultPDF", base64PDF);
        request.setAttribute("isPreviewReady", "true");
        return true;
    }

    /**
     * Writes a JSON response containing the base64-encoded PDF, filename, and any error message.
     *
     * @param request  HttpServletRequest containing the PDF data and filename as attributes
     * @param response HttpServletResponse where the JSON is written
     */
    private void generatePDFResponse(HttpServletRequest request, HttpServletResponse response) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("consultPDF", (String) request.getAttribute("consultPDF"));
        json.put("consultPDFName", (String) request.getAttribute("consultPDFName"));
        json.put("errorMessage", (String) request.getAttribute("errorMessage"));
        response.setContentType("text/javascript");
        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Generates a PDF filename in the format {@code yyyy_MM_dd_LastName.pdf} for the given patient.
     *
     * @param loggedInInfo  LoggedInInfo the authenticated session context
     * @param demographicNo int the patient demographic number
     * @return String the generated filename
     */
    private String generateFileName(LoggedInInfo loggedInInfo, int demographicNo) {
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        String demographicLastName = demographicManager.getDemographicFormattedName(loggedInInfo, demographicNo).split(", ")[0];

        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd");
        String formattedDate = dateFormat.format(currentDate);

        return formattedDate + "_" + demographicLastName + ".pdf";
    }

    String allergies;

    String appointmentDate;

    String appointmentHour;

    String appointmentMinute;

    String appointmentNotes;

    String appointmentPm;

    String appointmentTime;

    String clinicalInformation;

    String concurrentProblems;

    String currentMedications;

    String demographicNo;

    // Patient Will Book Field, can be either "1" or "0"
    String patientWillBook;

    String providerNo;

    String reasonForConsultation;

    String referalDate;

    String requestId;

    String sendTo;

    String service;

    String specialist;

    String status;

    String submission;

    String urgency;

    //multi-site
    String siteName;

    private String signatureImg;
    private String patientFirstName;
    private String patientLastName;
    private String patientAddress;
    private String patientPhone;
    private String patientWPhone;
    private String patientCellPhone;
    private String patientEmail;
    private String patientDOB;
    private String patientSex;
    private String patientHealthNum;
    private String patientHealthCardVersionCode;
    private String patientHealthCardType;
    private String patientAge;
    private String providerName;
    private String professionalSpecialistName;
    private String professionalSpecialistPhone;
    private String professionalSpecialistAddress;
    private String followUpDate;
    private boolean eReferral = false;
    private String eReferralService = "";
    private String eReferralId = null;
    private Integer hl7TextMessageId;

    private String letterheadName, letterheadAddress, letterheadPhone, letterheadFax;

    private Integer fdid;
    private String source;

    private String appointmentInstructions;
    private String appointmentInstructionsLabel;

    private String[] docNo;
    private String[] labNo;

	private String[] formNo;
	private String[] eFormNo;
	private String[] hrmNo;

	private String faxAccount;


    public String getProfessionalSpecialistName() {
        return (StringUtils.trimToEmpty(professionalSpecialistName));
    }

    @StrutsParameter
    public void setProfessionalSpecialistName(String professionalSpecialistName) {
        this.professionalSpecialistName = professionalSpecialistName;
    }

    public String getProfessionalSpecialistPhone() {
        return (StringUtils.trimToEmpty(professionalSpecialistPhone));
    }

    @StrutsParameter
    public void setProfessionalSpecialistPhone(String professionalSpecialistPhone) {
        this.professionalSpecialistPhone = professionalSpecialistPhone;
    }

    public String getProfessionalSpecialistAddress() {
        return (StringUtils.trimToEmpty(professionalSpecialistAddress));
    }

    @StrutsParameter
    public void setProfessionalSpecialistAddress(String professionalSpecialistAddress) {
        this.professionalSpecialistAddress = professionalSpecialistAddress;
    }

    public boolean iseReferral() {
        return eReferral;
    }

    @StrutsParameter
    public void seteReferral(boolean eReferral) {
        this.eReferral = eReferral;
    }

    public String geteReferralService() {
        return eReferralService;
    }

    @StrutsParameter
    public void seteReferralService(String eReferralService) {
        this.eReferralService = eReferralService;
    }

    public String geteReferralId() {
        return eReferralId;
    }

    @StrutsParameter
    public void seteReferralId(String eReferralId) {
        this.eReferralId = eReferralId;
    }

    public String getProviderName() {
        return (StringUtils.trimToEmpty(providerName));
    }

    @StrutsParameter
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getPatientAge() {
        return (StringUtils.trimToEmpty(patientAge));
    }

    @StrutsParameter
    public void setPatientAge(String patientAge) {
        this.patientAge = patientAge;
    }

    public String getAllergies() {
        return (StringUtils.trimToEmpty(allergies));
    }

    public String getAppointmentDate() {
        return (StringUtils.trimToEmpty(appointmentDate));
    }

    public String getAppointmentHour() {
        return (StringUtils.trimToEmpty(appointmentHour));
    }

    public String getAppointmentMinute() {
        return (StringUtils.trimToEmpty(appointmentMinute));
    }

    public String getAppointmentNotes() {
        return (StringUtils.trimToEmpty(appointmentNotes));
    }

    public String getAppointmentPm() {
        return (StringUtils.trimToEmpty(appointmentPm));
    }

    public String getAppointmentTime() {
        return (StringUtils.trimToEmpty(appointmentTime));
    }

    public String getClinicalInformation() {
        return (StringUtils.trimToEmpty(clinicalInformation));
    }

    public String getConcurrentProblems() {
        return (StringUtils.trimToEmpty(concurrentProblems));
    }

    public String getCurrentMedications() {
        return (StringUtils.trimToEmpty(currentMedications));
    }

    public String getDemographicNo() {
        return (StringUtils.trimToEmpty(demographicNo));
    }

    public String getPatientWillBook() {
        return patientWillBook;
    }

    public String getProviderNo() {
        return (StringUtils.trimToEmpty(providerNo));
    }

    public String getReasonForConsultation() {
        return (StringUtils.trimToEmpty(reasonForConsultation));
    }

    public String getReferalDate() {
        return (StringUtils.trimToEmpty(referalDate));
    }

    public String getRequestId() {
        return (StringUtils.trimToEmpty(requestId));
    }

    public String getSendTo() {
        return (StringUtils.trimToEmpty(sendTo));
    }

    public String getService() {
        return (StringUtils.trimToEmpty(service));
    }

    public String getSpecialist() {
        return (StringUtils.trimToEmpty(specialist));
    }

    public String getStatus() {
        return (StringUtils.trimToEmpty(status));
    }

    public String getSubmission() {
        return (StringUtils.trimToEmpty(submission));
    }

    public String getUrgency() {
        return (StringUtils.trimToEmpty(urgency));
    }


    @StrutsParameter
    public void setAllergies(String str) {
        allergies = str;
    }

    @StrutsParameter
    public void setAppointmentDate(String str) {
        appointmentDate = str;
    }

    @StrutsParameter
    public void setAppointmentHour(String str) {
        appointmentHour = str;
    }

    @StrutsParameter
    public void setAppointmentMinute(String str) {
        appointmentMinute = str;
    }

    @StrutsParameter
    public void setAppointmentNotes(String str) {
        appointmentNotes = str;
    }

    @StrutsParameter
    public void setAppointmentPm(String str) {
        appointmentPm = str;
    }

    @StrutsParameter
    public void setAppointmentTime(String str) {
        appointmentTime = str;
    }

    @StrutsParameter
    public void setClinicalInformation(String str) {
        clinicalInformation = str;
    }

    @StrutsParameter
    public void setConcurrentProblems(String str) {
        concurrentProblems = str;
    }

    @StrutsParameter
    public void setCurrentMedications(String str) {
        currentMedications = str;
    }

    @StrutsParameter
    public void setDemographicNo(String str) {
        demographicNo = str;
    }

    @StrutsParameter
    public void setPatientWillBook(String str) {
        this.patientWillBook = str;
    }

    @StrutsParameter
    public void setProviderNo(String str) {
        providerNo = str;
    }

    @StrutsParameter
    public void setReasonForConsultation(String str) {
        reasonForConsultation = str;
    }

    @StrutsParameter
    public void setReferalDate(String str) {
        referalDate = str;
    }

    @StrutsParameter
    public void setRequestId(String str) {
        requestId = str;
    }

    @StrutsParameter
    public void setSendTo(String str) {
        sendTo = str;
    }

    @StrutsParameter
    public void setService(String str) {
        service = str;
    }

    @StrutsParameter
    public void setSpecialist(String str) {
        specialist = str;
    }

    @StrutsParameter
    public void setStatus(String str) {
        status = str;
    }

    @StrutsParameter
    public void setSubmission(String str) {
        submission = str;
    }

    @StrutsParameter
    public void setUrgency(String str) {
        urgency = str;
    }

    public String getPatientName() {
        return (StringUtils.trimToEmpty(patientLastName + ", " + patientFirstName));
    }

    public String getPatientAddress() {
        return (StringUtils.trimToEmpty(patientAddress));
    }

    @StrutsParameter
    public void setPatientAddress(String patientAddress) {
        this.patientAddress = patientAddress;
    }

    public String getPatientPhone() {
        return (StringUtils.trimToEmpty(patientPhone));
    }

    @StrutsParameter
    public void setPatientPhone(String patientPhone) {
        this.patientPhone = patientPhone;
    }

    public String getPatientWPhone() {
        return (StringUtils.trimToEmpty(patientWPhone));
    }

    @StrutsParameter
    public void setPatientWPhone(String patientWPhone) {
        this.patientWPhone = patientWPhone;
    }

    public String getPatientCellPhone() {
        return StringUtils.trimToEmpty(patientCellPhone);
    }

    @StrutsParameter
    public void setPatientCellPhone(String patientCellPhone) {
        this.patientCellPhone = patientCellPhone;
    }

    @StrutsParameter
    public void setPatientEmail(String patientEmail) {
        this.patientEmail = patientEmail;
    }

    public String getPatientEmail() {
        return (StringUtils.trimToEmpty(patientEmail));
    }

    public String getPatientDOB() {
        return (StringUtils.trimToEmpty(patientDOB));
    }

    @StrutsParameter
    public void setPatientDOB(String patientDOB) {
        this.patientDOB = patientDOB;
    }

    public String getPatientSex() {
        return (StringUtils.trimToEmpty(patientSex));
    }

    @StrutsParameter
    public void setPatientSex(String patientSex) {
        this.patientSex = patientSex;
    }

    public String getPatientHealthNum() {
        return (StringUtils.trimToEmpty(patientHealthNum));
    }

    @StrutsParameter
    public void setPatientHealthNum(String patientHealthNum) {
        this.patientHealthNum = patientHealthNum;
    }

    public String getPatientHealthCardVersionCode() {
        return (StringUtils.trimToEmpty(patientHealthCardVersionCode));
    }

    @StrutsParameter
    public void setPatientHealthCardVersionCode(String patientHealthCardVersionCode) {
        this.patientHealthCardVersionCode = patientHealthCardVersionCode;
    }

    public String getPatientHealthCardType() {
        return (StringUtils.trimToEmpty(patientHealthCardType));
    }

    @StrutsParameter
    public void setPatientHealthCardType(String patientHealthCardType) {
        this.patientHealthCardType = patientHealthCardType;
    }

    public Integer getHl7TextMessageId() {
        return hl7TextMessageId;
    }

    @StrutsParameter
    public void setHl7TextMessageId(Integer hl7TextMessageId) {
        this.hl7TextMessageId = hl7TextMessageId;
    }

    public String getPatientFirstName() {
        return patientFirstName;
    }

    @StrutsParameter
    public void setPatientFirstName(String patientFirstName) {
        this.patientFirstName = patientFirstName;
    }

    public String getPatientLastName() {
        return patientLastName;
    }

    @StrutsParameter
    public void setPatientLastName(String patientLastName) {
        this.patientLastName = patientLastName;
    }

    /**
     * Builds the URL for the ORU_R01 lab sending page, including the context path and
     * patient demographic query parameters. The returned URL is HTML-escaped for safe
     * embedding in JSP output.
     *
     * @param request HttpServletRequest used to obtain the context path
     * @return String the HTML-escaped URL string with query parameters
     */
    public String getOruR01UrlString(HttpServletRequest request) {
        // /lab/CA/ALL/sendOruR01.jsp

        StringBuilder sb = new StringBuilder();

        sb.append(request.getContextPath());
        sb.append("/lab/CA/ALL/sendOruR01.jsp");

        HashMap<String, Object> queryParameters = new HashMap<String, Object>();

        // buildQueryString will take null into account
        queryParameters.put("hl7TextMessageId", hl7TextMessageId);
        queryParameters.put("clientFirstName", patientFirstName);
        queryParameters.put("clientLastName", patientLastName);
        queryParameters.put("clientHin", patientHealthNum);
        queryParameters.put("clientBirthDate", patientDOB);
        queryParameters.put("clientGender", patientSex);

        sb.append(WebUtils.buildQueryString(queryParameters));

        return (StringEscapeUtils.escapeHtml4(sb.toString()));
    }

    /**
     * Returns the follow-up date for the consultation request.
     *
     * @return String the follow-up date in yyyy-MM-dd or yyyy/MM/dd format
     */
    public String getFollowUpDate() {
        return followUpDate;
    }

    /**
     * Sets the follow-up date for the consultation request.
     *
     * @param followUpDate String the follow-up date in yyyy-MM-dd or yyyy/MM/dd format
     */
    @StrutsParameter
    public void setFollowUpDate(String followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getSiteName() {
        if (siteName == null) {
            siteName = new String();
        }
        return siteName;
    }

    @StrutsParameter
    public void setSiteName(String str) {
        this.siteName = str;
    }

    public String getSignatureImg() {
        return signatureImg;
    }

    @StrutsParameter
    public void setSignatureImg(String signatureImg) {
        this.signatureImg = signatureImg;
    }

    public String getLetterheadName() {
        return letterheadName;
    }

    @StrutsParameter
    public void setLetterheadName(String letterheadName) {
        this.letterheadName = letterheadName;
    }

    public String getLetterheadAddress() {
        return letterheadAddress;
    }

    @StrutsParameter
    public void setLetterheadAddress(String letterheadAddress) {
        this.letterheadAddress = letterheadAddress;
    }

    public String getLetterheadPhone() {
        return letterheadPhone;
    }

    @StrutsParameter
    public void setLetterheadPhone(String letterheadPhone) {
        this.letterheadPhone = letterheadPhone;
    }

    public String getLetterheadFax() {
        return letterheadFax;
    }

    @StrutsParameter
    public void setLetterheadFax(String letterheadFax) {
        this.letterheadFax = letterheadFax;
    }

    public Integer getFdid() {
        return fdid;
    }

    @StrutsParameter
    public void setFdid(Integer fdid) {
        this.fdid = fdid;
    }

    public String getSource() {
        return source;
    }

    @StrutsParameter
    public void setSource(String source) {
        this.source = source;
    }

    public String getAppointmentInstructions() {
        return appointmentInstructions;
    }

    @StrutsParameter
    public void setAppointmentInstructions(String appointmentInstructions) {
        this.appointmentInstructions = appointmentInstructions;
    }

    public String getAppointmentInstructionsLabel() {
        return appointmentInstructionsLabel;
    }

    @StrutsParameter
    public void setAppointmentInstructionsLabel(String appointmentInstructionsLabel) {
        this.appointmentInstructionsLabel = appointmentInstructionsLabel;
    }

    public String[] getDocNo() {
        if (docNo == null) {
            return new String[]{};
        }
        return docNo;
    }

    @StrutsParameter
    public void setDocNo(String[] docNo) {
        this.docNo = docNo;
    }

    public String[] getLabNo() {
        if (labNo == null) {
            return new String[]{};
        }
        return labNo;
    }

    @StrutsParameter
    public void setLabNo(String[] labNo) {
        this.labNo = labNo;
    }

    public String[] getFormNo() {
        if (formNo == null) {
            return new String[]{};
        }
        return formNo;
    }

    @StrutsParameter
    public void setFormNo(String[] formNo) {
        this.formNo = formNo;
    }

    public String[] geteFormNo() {
        if (eFormNo == null) {
            return new String[]{};
        }
        return eFormNo;
    }

    @StrutsParameter
    public void seteFormNo(String[] eFormNo) {
        this.eFormNo = eFormNo;
    }

	public String[] getHrmNo() {
		if (hrmNo == null) {
			return new String[]{};
		}
		return hrmNo;
	}
	
	@StrutsParameter
	public void setHrmNo(String[] hrmNo) {
		this.hrmNo = hrmNo;
	}

	public String getFaxAccount() {
		return faxAccount;
	}

	@StrutsParameter
	public void setFaxAccount(String faxAccount) {
		this.faxAccount = faxAccount;
	}
}
