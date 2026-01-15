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

import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.commn.model.*;
import ca.openosp.openo.managers.*;
import ca.openosp.openo.utility.*;
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
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.OruR01;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.OruR01.ObservationData;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.RefI12;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.SendingUtils;
import ca.openosp.openo.commn.model.enumerator.DocumentType;
import ca.openosp.openo.commn.model.enumerator.ModuleType;
import ca.openosp.openo.documentManager.DocumentAttachmentManager;
import com.itextpdf.text.DocumentException;
import ca.openosp.openo.documentManager.EDoc;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.fax.core.FaxRecipient;
import ca.openosp.openo.managers.FaxManager.TransactionType;
import ca.openosp.OscarProperties;
import ca.openosp.openo.encounter.data.EctFormData;
import ca.openosp.openo.lab.ca.all.pageUtil.LabPDFCreator;
import ca.openosp.openo.lab.ca.on.CommonLabResultData;
import ca.openosp.openo.lab.ca.on.LabResultData;

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
        ;
        ConsultationRequestExtDao consultationRequestExtDao = (ConsultationRequestExtDao) SpringUtils.getBean(ConsultationRequestExtDao.class);
        ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

        String[] format = new String[]{"yyyy-MM-dd", "yyyy/MM/dd"};

        if (submission.startsWith("Submit")) {

            try {
                if (newSignature) {
		   DigitalSignature signature = digitalSignatureManager.processAndSaveDigitalSignature(loggedInInfo, signatureImg, Integer.parseInt(demographicNo), ModuleType.CONSULTATION);
                   if (signature != null) {
                        signatureId = "" + signature.getId();
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
                if (newSignature) {
		    DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
		    DigitalSignature signature = digitalSignatureManager.processAndSaveDigitalSignature(loggedInInfo, signatureImg, Integer.parseInt(demographicNo), ModuleType.CONSULTATION);
                    if (signature != null) {
                        signatureId = "" + signature.getId();
                    } else {
                        signatureId = null;
                    }
                } else {
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

    private ConsultationRequestExt createExtEntry(String requestId, String name, String value) {
        ConsultationRequestExt obj = new ConsultationRequestExt();
        obj.setDateCreated(new Date());
        obj.setKey(name);
        obj.setValue(value);
        obj.setRequestId(Integer.parseInt(requestId));
        return obj;
    }

    private void doHl7Send(LoggedInInfo loggedInInfo, Integer consultationRequestId) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, IOException, HL7Exception, ServletException {

        ConsultationRequestDao consultationRequestDao = (ConsultationRequestDao) SpringUtils.getBean(ConsultationRequestDao.class);
        ;
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

    public void setProfessionalSpecialistName(String professionalSpecialistName) {
        this.professionalSpecialistName = professionalSpecialistName;
    }

    public String getProfessionalSpecialistPhone() {
        return (StringUtils.trimToEmpty(professionalSpecialistPhone));
    }

    public void setProfessionalSpecialistPhone(String professionalSpecialistPhone) {
        this.professionalSpecialistPhone = professionalSpecialistPhone;
    }

    public String getProfessionalSpecialistAddress() {
        return (StringUtils.trimToEmpty(professionalSpecialistAddress));
    }

    public void setProfessionalSpecialistAddress(String professionalSpecialistAddress) {
        this.professionalSpecialistAddress = professionalSpecialistAddress;
    }

    public boolean iseReferral() {
        return eReferral;
    }

    public void seteReferral(boolean eReferral) {
        this.eReferral = eReferral;
    }

    public String geteReferralService() {
        return eReferralService;
    }

    public void seteReferralService(String eReferralService) {
        this.eReferralService = eReferralService;
    }

    public String geteReferralId() {
        return eReferralId;
    }

    public void seteReferralId(String eReferralId) {
        this.eReferralId = eReferralId;
    }

    public String getProviderName() {
        return (StringUtils.trimToEmpty(providerName));
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getPatientAge() {
        return (StringUtils.trimToEmpty(patientAge));
    }

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


    public void setAllergies(String str) {
        allergies = str;
    }

    public void setAppointmentDate(String str) {
        appointmentDate = str;
    }

    public void setAppointmentHour(String str) {
        appointmentHour = str;
    }

    public void setAppointmentMinute(String str) {
        appointmentMinute = str;
    }

    public void setAppointmentNotes(String str) {
        appointmentNotes = str;
    }

    public void setAppointmentPm(String str) {
        appointmentPm = str;
    }

    public void setAppointmentTime(String str) {
        appointmentTime = str;
    }

    public void setClinicalInformation(String str) {
        clinicalInformation = str;
    }

    public void setConcurrentProblems(String str) {
        concurrentProblems = str;
    }

    public void setCurrentMedications(String str) {
        currentMedications = str;
    }

    public void setDemographicNo(String str) {
        demographicNo = str;
    }

    public void setPatientWillBook(String str) {
        this.patientWillBook = str;
    }

    public void setProviderNo(String str) {
        providerNo = str;
    }

    public void setReasonForConsultation(String str) {
        reasonForConsultation = str;
    }

    public void setReferalDate(String str) {
        referalDate = str;
    }

    public void setRequestId(String str) {
        requestId = str;
    }

    public void setSendTo(String str) {
        sendTo = str;
    }

    public void setService(String str) {
        service = str;
    }

    public void setSpecialist(String str) {
        specialist = str;
    }

    public void setStatus(String str) {
        status = str;
    }

    public void setSubmission(String str) {
        submission = str;
    }

    public void setUrgency(String str) {
        urgency = str;
    }

    public String getPatientName() {
        return (StringUtils.trimToEmpty(patientLastName + ", " + patientFirstName));
    }

    public String getPatientAddress() {
        return (StringUtils.trimToEmpty(patientAddress));
    }

    public void setPatientAddress(String patientAddress) {
        this.patientAddress = patientAddress;
    }

    public String getPatientPhone() {
        return (StringUtils.trimToEmpty(patientPhone));
    }

    public void setPatientPhone(String patientPhone) {
        this.patientPhone = patientPhone;
    }

    public String getPatientWPhone() {
        return (StringUtils.trimToEmpty(patientWPhone));
    }

    public void setPatientWPhone(String patientWPhone) {
        this.patientWPhone = patientWPhone;
    }

    public String getPatientCellPhone() {
        return StringUtils.trimToEmpty(patientCellPhone);
    }

    public void setPatientCellPhone(String patientCellPhone) {
        this.patientCellPhone = patientCellPhone;
    }

    public void setPatientEmail(String patientEmail) {
        this.patientEmail = patientEmail;
    }

    public String getPatientEmail() {
        return (StringUtils.trimToEmpty(patientEmail));
    }

    public String getPatientDOB() {
        return (StringUtils.trimToEmpty(patientDOB));
    }

    public void setPatientDOB(String patientDOB) {
        this.patientDOB = patientDOB;
    }

    public String getPatientSex() {
        return (StringUtils.trimToEmpty(patientSex));
    }

    public void setPatientSex(String patientSex) {
        this.patientSex = patientSex;
    }

    public String getPatientHealthNum() {
        return (StringUtils.trimToEmpty(patientHealthNum));
    }

    public void setPatientHealthNum(String patientHealthNum) {
        this.patientHealthNum = patientHealthNum;
    }

    public String getPatientHealthCardVersionCode() {
        return (StringUtils.trimToEmpty(patientHealthCardVersionCode));
    }

    public void setPatientHealthCardVersionCode(String patientHealthCardVersionCode) {
        this.patientHealthCardVersionCode = patientHealthCardVersionCode;
    }

    public String getPatientHealthCardType() {
        return (StringUtils.trimToEmpty(patientHealthCardType));
    }

    public void setPatientHealthCardType(String patientHealthCardType) {
        this.patientHealthCardType = patientHealthCardType;
    }

    public Integer getHl7TextMessageId() {
        return hl7TextMessageId;
    }

    public void setHl7TextMessageId(Integer hl7TextMessageId) {
        this.hl7TextMessageId = hl7TextMessageId;
    }

    public String getPatientFirstName() {
        return patientFirstName;
    }

    public void setPatientFirstName(String patientFirstName) {
        this.patientFirstName = patientFirstName;
    }

    public String getPatientLastName() {
        return patientLastName;
    }

    public void setPatientLastName(String patientLastName) {
        this.patientLastName = patientLastName;
    }

    /**
     * This url will include the context path.
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
     * @return the followUpDate
     */
    public String getFollowUpDate() {
        return followUpDate;
    }

    /**
     * @param followUpDate the followUpDate to set
     */
    public void setFollowUpDate(String followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getSiteName() {
        if (siteName == null) {
            siteName = new String();
        }
        return siteName;
    }

    public void setSiteName(String str) {
        this.siteName = str;
    }

    public String getSignatureImg() {
        return signatureImg;
    }

    public void setSignatureImg(String signatureImg) {
        this.signatureImg = signatureImg;
    }

    public String getLetterheadName() {
        return letterheadName;
    }

    public void setLetterheadName(String letterheadName) {
        this.letterheadName = letterheadName;
    }

    public String getLetterheadAddress() {
        return letterheadAddress;
    }

    public void setLetterheadAddress(String letterheadAddress) {
        this.letterheadAddress = letterheadAddress;
    }

    public String getLetterheadPhone() {
        return letterheadPhone;
    }

    public void setLetterheadPhone(String letterheadPhone) {
        this.letterheadPhone = letterheadPhone;
    }

    public String getLetterheadFax() {
        return letterheadFax;
    }

    public void setLetterheadFax(String letterheadFax) {
        this.letterheadFax = letterheadFax;
    }

    public Integer getFdid() {
        return fdid;
    }

    public void setFdid(Integer fdid) {
        this.fdid = fdid;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAppointmentInstructions() {
        return appointmentInstructions;
    }

    public void setAppointmentInstructions(String appointmentInstructions) {
        this.appointmentInstructions = appointmentInstructions;
    }

    public String getAppointmentInstructionsLabel() {
        return appointmentInstructionsLabel;
    }

    public void setAppointmentInstructionsLabel(String appointmentInstructionsLabel) {
        this.appointmentInstructionsLabel = appointmentInstructionsLabel;
    }

    public String[] getDocNo() {
        if (docNo == null) {
            return new String[]{};
        }
        return docNo;
    }

    public void setDocNo(String[] docNo) {
        this.docNo = docNo;
    }

    public String[] getLabNo() {
        if (labNo == null) {
            return new String[]{};
        }
        return labNo;
    }

    public void setLabNo(String[] labNo) {
        this.labNo = labNo;
    }

    public String[] getFormNo() {
        if (formNo == null) {
            return new String[]{};
        }
        return formNo;
    }

    public void setFormNo(String[] formNo) {
        this.formNo = formNo;
    }

    public String[] geteFormNo() {
        if (eFormNo == null) {
            return new String[]{};
        }
        return eFormNo;
    }

    public void seteFormNo(String[] eFormNo) {
        this.eFormNo = eFormNo;
    }

	public String[] getHrmNo() {
		if(hrmNo == null) {
			return new String[]{};
		}
		return hrmNo;
	}
	
	public void setHrmNo(String[] hrmNo) {
		this.hrmNo = hrmNo;
	}

	public String getFaxAccount() {
		return faxAccount;
	}

	public void setFaxAccount(String faxAccount) {
		this.faxAccount = faxAccount;
	}
}
