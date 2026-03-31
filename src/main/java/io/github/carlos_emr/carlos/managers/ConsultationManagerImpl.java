/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.openpdf.text.DocumentException;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao.Module;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar.OruR01;
import io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar.OruR01.ObservationData;
import io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar.RefI12;
import io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar.SendingUtils;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestArchive;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExtArchive;
import io.github.carlos_emr.carlos.commn.model.ConsultationResponse;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EReferAttachment;
import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.consultations.ConsultationRequestSearchFilter;
import io.github.carlos_emr.carlos.consultations.ConsultationRequestSearchFilter.SORTDIR;
import io.github.carlos_emr.carlos.consultations.ConsultationResponseSearchFilter;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.webserv.rest.conversion.OtnEconsultConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationAttachment;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestSearchResult;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationResponseSearchResult;
import io.github.carlos_emr.carlos.webserv.rest.to.model.OtnEconsult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v26.message.ORU_R01;
import ca.uhn.hl7v2.model.v26.message.REF_I12;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;

/**
 * Spring-managed implementation of the {@link ConsultationManager} interface.
 *
 * <p>Provides the business logic for consultation request/response management, including
 * search, CRUD, HL7 electronic referral transmission, eConsult import, attachment handling,
 * PDF generation, and archiving. All patient data access is protected by
 * {@link SecurityInfoManager} privilege checks on the {@code _con} security object.
 *
 * <p>HL7 electronic referral ({@link #doHl7Send}) sends a REF_I12 message followed by
 * ORU_R01 messages for each attached document and lab result. Lab PDFs are generated
 * on-the-fly using {@link LabPDFCreator}.
 *
 * @see ConsultationManager
 * @since 2014 (McMaster University)
 */
@Service
public class ConsultationManagerImpl implements ConsultationManager {

    @Autowired
    ConsultRequestDao consultationRequestDao;
    @Autowired
    ConsultResponseDao consultationResponseDao;
    @Autowired
    ConsultationServiceDao serviceDao;
    @Autowired
    ProfessionalSpecialistDao professionalSpecialistDao;
    @Autowired
    ConsultDocsDao requestDocDao;
    @Autowired
    ConsultResponseDocDao responseDocDao;
    @Autowired
    PropertyDao propertyDao;
    @Autowired
    Hl7TextInfoDao hl7TextInfoDao;
    @Autowired
    ClinicDAO clinicDao;
    @Autowired
    private ConsultDocsDao consultDocsDao;

    @Autowired
    private FormsManager formsManager;
    @Autowired
    private NioFileManager nioFileManager;

    @Autowired
    DemographicManager demographicManager;
    @Autowired
    SecurityInfoManager securityInfoManager;
    @Autowired
    ConsultationRequestExtDao consultationRequestExtDao;
    @Autowired
    ConsultationRequestExtArchiveDao consultationRequestExtArchiveDao;
    @Autowired
    ConsultationRequestArchiveDao consultationRequestArchiveDao;
    @Autowired
    EReferAttachmentDao eReferAttachmentDao;
    @Autowired
    DocumentManager documentManager;
    @Autowired
    private DocumentAttachmentManager documentAttachmentManager;

    private final Logger logger = MiscUtils.getLogger();

    public final String CON_REQUEST_ENABLED = "consultRequestEnabled";
    public final String CON_RESPONSE_ENABLED = "consultResponseEnabled";
    public final String ENABLED_YES = "Y";

    @Override
    public List<ConsultationRequestSearchResult> search(LoggedInInfo loggedInInfo, ConsultationRequestSearchFilter filter) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        List<ConsultationRequestSearchResult> r = new ArrayList<ConsultationRequestSearchResult>();
        List<Object[]> result = consultationRequestDao.search(filter);

        for (Object[] items : result) {
            ConsultationRequest consultRequest = (ConsultationRequest) items[0];
            LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.searchRequest", "id=" + consultRequest.getId());
            r.add(convertToRequestSearchResult(items));
        }
        return r;
    }

    @Override
    public List<ConsultationResponseSearchResult> search(LoggedInInfo loggedInInfo, ConsultationResponseSearchFilter filter) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        List<ConsultationResponseSearchResult> r = new ArrayList<ConsultationResponseSearchResult>();
        List<Object[]> result = consultationResponseDao.search(filter);

        for (Object[] items : result) {
            ConsultationResponse consultResponse = (ConsultationResponse) items[0];
            LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.searchResponse", "id=" + consultResponse.getId());
            r.add(convertToResponseSearchResult(items));
        }
        return r;
    }

    @Override
    public int getConsultationCount(ConsultationRequestSearchFilter filter) {
        return consultationRequestDao.getConsultationCount2(filter);
    }

    @Override
    public int getConsultationCount(ConsultationResponseSearchFilter filter) {
        return consultationResponseDao.getConsultationCount(filter);
    }

    @Override
    public boolean hasOutstandingConsultations(LoggedInInfo loggedInInfo, Integer demographicNo) {
        //Outstanding consultations = Incomplete consultation requests > 1 month
        ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
        filter.setDemographicNo(demographicNo);
        filter.setNumToReturn(100);
        filter.setSortDir(SORTDIR.asc);

        List<ConsultationRequestSearchResult> results = search(loggedInInfo, filter);
        boolean outstanding = false;
        for (ConsultationRequestSearchResult result : results) {
            if (result.getReferralDate() != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(result.getReferralDate());
                cal.roll(Calendar.MONTH, true);
                if (new Date().after(cal.getTime())) {
                    outstanding = true;
                    break;
                }
            }
        }
        return outstanding;
    }

    @Override
    public ConsultationRequest getRequest(LoggedInInfo loggedInInfo, Integer id) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        ConsultationRequest request = consultationRequestDao.find(id);
        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.getRequest", "id=" + request.getId());

        return request;
    }

    @Override
    public ConsultationResponse getResponse(LoggedInInfo loggedInInfo, Integer id) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        ConsultationResponse response = consultationResponseDao.find(id);
        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.getResponse", "id=" + response.getId());

        return response;
    }

    @Override
    public List<ConsultationServices> getConsultationServices() {
        List<ConsultationServices> services = serviceDao.findActive();
        for (ConsultationServices service : services) {
            if (service.getServiceDesc().equals(serviceDao.REFERRING_DOCTOR)) {
                services.remove(service);
                break;
            }
        }
        return services;
    }

    @Override
    public List<ProfessionalSpecialist> getReferringDoctorList() {
        ConsultationServices service = serviceDao.findReferringDoctorService(serviceDao.ACTIVE_ONLY);
        return (service == null) ? null : service.getSpecialists();
    }

    @Override
    public ProfessionalSpecialist getProfessionalSpecialist(Integer id) {
        return professionalSpecialistDao.find(id);
    }

    @Override
    public void saveConsultationRequest(LoggedInInfo loggedInInfo, ConsultationRequest request) {
        if (request.getId() == null) { //new consultation request
            checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);

            ProfessionalSpecialist specialist = request.getProfessionalSpecialist();
            request.setProfessionalSpecialist(null);
            consultationRequestDao.persist(request);

            request.setProfessionalSpecialist(specialist);
            consultationRequestDao.merge(request);
        } else {
            checkPrivilege(loggedInInfo, SecurityInfoManager.UPDATE);

            consultationRequestDao.merge(request);
        }
        // Batch saves the provided extras if any exist
        if (!request.getExtras().isEmpty()) {
            saveOrUpdateExts(request.getId(), request.getExtras());
            // Sets the request's extras to all current extras with the updated information
            List<ConsultationRequestExt> extras = consultationRequestExtDao.getConsultationRequestExts(request.getId());
            request.setExtras(extras);
        }
        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.saveConsultationRequest", "id=" + request.getId());
    }

    @Override
    public void saveConsultationResponse(LoggedInInfo loggedInInfo, ConsultationResponse response) {
        if (response.getId() == null) { //new consultation response
            checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);

            consultationResponseDao.persist(response);
        } else {
            checkPrivilege(loggedInInfo, SecurityInfoManager.UPDATE);

            consultationResponseDao.merge(response);
        }
        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.saveConsultationResponse", "id=" + response.getId());
    }

    @Override
    public List<ConsultDocs> getConsultRequestDocs(LoggedInInfo loggedInInfo, Integer requestId) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        List<ConsultDocs> docs = requestDocDao.findByRequestId(requestId);
        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.getConsultRequestDocs", "consult id=" + requestId);

        return docs;
    }

    @Override
    public List<ConsultResponseDoc> getConsultResponseDocs(LoggedInInfo loggedInInfo, Integer responseId) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        List<ConsultResponseDoc> docs = responseDocDao.findByResponseId(responseId);
        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.getConsultResponseDocs", "consult id=" + responseId);

        return docs;
    }

    @Override
    public void saveConsultRequestDoc(LoggedInInfo loggedInInfo, ConsultDocs doc) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.UPDATE);

        if (doc.getId() == null) { //new consultation attachment
            requestDocDao.persist(doc);
        } else {
            requestDocDao.merge(doc); //only used for setting doc "deleted"
        }
        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.saveConsultRequestDoc", "id=" + doc.getId());
    }

    @Override
    public void saveConsultResponseDoc(LoggedInInfo loggedInInfo, ConsultResponseDoc doc) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.UPDATE);

        if (doc.getId() == null) { //new consultation attachment
            responseDocDao.persist(doc);
        } else {
            responseDocDao.merge(doc); //only used for setting doc "deleted"
        }
        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.saveConsultResponseDoc", "id=" + doc.getId());
    }

    @Override
    public void enableConsultRequestResponse(boolean conRequest, boolean conResponse) {
        Property consultRequestEnabled = new Property(CON_REQUEST_ENABLED);
        Property consultResponseEnabled = new Property(CON_RESPONSE_ENABLED);

        List<Property> results = propertyDao.findByName(CON_REQUEST_ENABLED);
        if (results.size() > 0) consultRequestEnabled = results.get(0);
        results = propertyDao.findByName(CON_RESPONSE_ENABLED);
        if (results.size() > 0) consultResponseEnabled = results.get(0);

        consultRequestEnabled.setValue(conRequest ? ENABLED_YES : null);
        consultResponseEnabled.setValue(conResponse ? ENABLED_YES : null);

        propertyDao.merge(consultRequestEnabled);
        propertyDao.merge(consultResponseEnabled);

        ConsultationServices referringDocService = serviceDao.findReferringDoctorService(serviceDao.WITH_INACTIVE);
        if (referringDocService == null) referringDocService = new ConsultationServices(serviceDao.REFERRING_DOCTOR);
        if (conResponse) referringDocService.setActive(serviceDao.ACTIVE);
        else referringDocService.setActive(serviceDao.INACTIVE);

        serviceDao.merge(referringDocService);
    }

    @Override
    public boolean isConsultRequestEnabled() {
        List<Property> results = propertyDao.findByName(CON_REQUEST_ENABLED);
        if (results.size() > 0 && ENABLED_YES.equals(results.get(0).getValue())) return true;
        return false;
    }

    @Override
    public boolean isConsultResponseEnabled() {
        List<Property> results = propertyDao.findByName(CON_RESPONSE_ENABLED);
        if (results.size() > 0 && ENABLED_YES.equals(results.get(0).getValue())) return true;
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends the consultation request via HL7 REF_I12, then iterates through all
     * attached documents (as ORU_R01 with binary data) and labs (as ORU_R01 with
     * PDF bytes generated by {@link LabPDFCreator}).
     */
    @Override
    public void doHl7Send(LoggedInInfo loggedInInfo, Integer consultationRequestId) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, IOException, HL7Exception, ServletException, DocumentException {
        checkPrivilege(loggedInInfo, securityInfoManager.READ);

        ConsultationRequest consultationRequest = consultationRequestDao.find(consultationRequestId);
        ProfessionalSpecialist professionalSpecialist = professionalSpecialistDao.find(consultationRequest.getSpecialistId());
        Clinic clinic = clinicDao.getClinic();

        // set status now so the remote version shows this status
        consultationRequest.setStatus("2");

        REF_I12 refI12 = RefI12.makeRefI12(clinic, consultationRequest);
        SendingUtils.send(loggedInInfo, refI12, professionalSpecialist);

        // save after the sending just in case the sending fails.
        consultationRequestDao.merge(consultationRequest);

        //--- send attachments ---
        Provider sendingProvider = loggedInInfo.getLoggedInProvider();
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
        }
    }

    /**
     * Import a PDF formatted OTN eConsult.
     *
     * @throws Exception
     */
    @Override
    public void importEconsult(LoggedInInfo loggedInInfo, OtnEconsult otnEconsult) throws Exception {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);

        // convert to a CARLOS Document
        OtnEconsultConverter otnEconsultConverter = new OtnEconsultConverter();
        Document document = otnEconsultConverter.getAsDomainObject(loggedInInfo, otnEconsult);
        CtlDocumentPK ctlDocumentPk = new CtlDocumentPK(Module.DEMOGRAPHIC.getName(), otnEconsult.getDemographicNo(), null);
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setStatus("A");
        ctlDocument.setId(ctlDocumentPk);

        // save the document
        document = documentManager.addDocument(loggedInInfo, document, ctlDocument);

        if (document == null) {
            throw new Exception("Unknown exception during document save");
        }

        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.importEconsult", "eConsult saved for demographic " + otnEconsult.getDemographicNo());

    }

    @Override
    public List<Document> getEconsultDocuments(LoggedInInfo loggedInInfo, int demographicNo) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        return documentManager.getDemographicDocumentsByDocumentType(loggedInInfo, demographicNo, DocumentDao.DocumentType.ECONSULT);
    }

    /**
     * Gets attachments for use on an eReferral for the provided demographic number. It only gets the oldest prepped attachments within the past hour.
     *
     * @param loggedInInfo  The current user's logged in info
     * @param request       The HttpRequest for printing any forms
     * @param response      The HttpResponse for printing any forms
     * @param demographicNo The demographic number to get the attachments for
     * @return List of ConsultationAttachments containing the file name and data and the attachment id and type,
     * @throws PDFGenerationException Thrown if an error occurs while generating pdf
     */
    @Override
    public List<ConsultationAttachment> getEReferAttachments(LoggedInInfo loggedInInfo, HttpServletRequest request, HttpServletResponse response, Integer demographicNo) throws PDFGenerationException {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, -1);
        EReferAttachment eReferAttachment = eReferAttachmentDao.getRecentByDemographic(demographicNo, calendar.getTime());
        if (eReferAttachment == null) {
            return Collections.emptyList();
        }

        List<ConsultationAttachment> consultationAttachments = new ArrayList<>();
        for (EReferAttachmentData eReferAttachmentData : eReferAttachment.getAttachments()) {
            try {
                ConsultationAttachment consultationAttachment = null;
                switch (eReferAttachmentData.getLabType()) {
                    case ConsultDocs.DOCTYPE_EFORM:
                        Path eFormPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.EFORM, eReferAttachmentData.getLabId());
                        String eFormName = String.format("EForm_%03d.pdf", eReferAttachmentData.getLabId());
                        consultationAttachment = new ConsultationAttachment(eReferAttachmentData.getLabId(), DocumentType.EFORM.getType(), eFormName, Files.readAllBytes(eFormPDFPath));
                        break;
                    case ConsultDocs.DOCTYPE_DOC:
                        Path eDocPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.DOC, eReferAttachmentData.getLabId());
                        String eDocName = String.format("Doc_%03d.pdf", eReferAttachmentData.getLabId());
                        consultationAttachment = new ConsultationAttachment(eReferAttachmentData.getLabId(), DocumentType.DOC.getType(), eDocName, Files.readAllBytes(eDocPDFPath));
                        break;
                    case ConsultDocs.DOCTYPE_LAB:
                        Path labPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.LAB, eReferAttachmentData.getLabId());
                        String labName = String.format("Lab_%03d.pdf", eReferAttachmentData.getLabId());
                        consultationAttachment = new ConsultationAttachment(eReferAttachmentData.getLabId(), DocumentType.LAB.getType(), labName, Files.readAllBytes(labPDFPath));
                        break;
                    case ConsultDocs.DOCTYPE_HRM:
                        Path hrmPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.HRM, eReferAttachmentData.getLabId());
                        String hrmName = String.format("HRM_%03d.pdf", eReferAttachmentData.getLabId());
                        consultationAttachment = new ConsultationAttachment(eReferAttachmentData.getLabId(), DocumentType.HRM.getType(), hrmName, Files.readAllBytes(hrmPDFPath));
                        break;
                    // Checkout the comment in ConsultationWebService:getEReferAttachments() for more details
                    // case ConsultDocs.DOCTYPE_FORM:
                    // 	Path formPDFPath = formsManager.renderForm(request, response, eReferAttachmentData.getLabId(), demographicNo);
                    // 	String formName = String.format("Form_%03d.pdf", eReferAttachmentData.getLabId());
                    // 	consultationAttachment = new ConsultationAttachment(eReferAttachmentData.getLabId(), DocumentType.FORM.getType(), formName, Files.readAllBytes(formPDFPath));
                    // 	break;
                    default:
                        break;
                }
                if (consultationAttachment != null) {
                    consultationAttachments.add(consultationAttachment);
                }
            } catch (IOException e) {
                throw new PDFGenerationException("Attachment " + eReferAttachmentData.getLabType() + " " + eReferAttachmentData.getLabId() + " encountered an error while generating the file data", e);
            }
        }

        // Archives the retrieved attachments so they can't be retrieved again
        eReferAttachment.setArchived(true);
        eReferAttachmentDao.merge(eReferAttachment);

        return consultationAttachments;
    }

    /**
     * Maps a raw JPA projection array to a ConsultationRequestSearchResult DTO.
     * Array indices match the DAO SELECT column order: [0]=ConsultationRequest,
     * [1]=ProfessionalSpecialist, [2]=ConsultationServices, [3]=Demographic, [4]=Provider.
     *
     * @param items Object[] the JPA projection row
     * @return ConsultationRequestSearchResult the populated DTO
     */
    private ConsultationRequestSearchResult convertToRequestSearchResult(Object[] items) {
        ConsultationRequestSearchResult result = new ConsultationRequestSearchResult();

        ConsultationRequest consultRequest = (ConsultationRequest) items[0];
        ProfessionalSpecialist professionalSpecialist = (ProfessionalSpecialist) items[1];
        ConsultationServices consultationServices = (ConsultationServices) items[2];
        Demographic demographic = (Demographic) items[3];
        Provider provider = (Provider) items[4];


        result.setAppointmentDate(joinDateAndTime(consultRequest.getAppointmentDate(), consultRequest.getAppointmentTime()));
        result.setConsultant(professionalSpecialist);
        result.setDemographic(demographic);
        result.setId(consultRequest.getId());
        result.setLastFollowUp(consultRequest.getFollowUpDate());
        result.setMrp(provider);
        result.setReferralDate(consultRequest.getReferralDate());
        result.setServiceName(consultationServices.getServiceDesc());
        result.setStatus(consultRequest.getStatus());
        result.setUrgency(consultRequest.getUrgency());

        if (consultRequest.getSendTo() != null && !consultRequest.getSendTo().isEmpty() && !consultRequest.getSendTo().equals("-1")) {
            result.setTeamName(consultRequest.getSendTo());
        }

        return result;
    }

    /**
     * Maps a raw JPA projection array to a ConsultationResponseSearchResult DTO.
     * Array indices match the DAO SELECT column order: [0]=ConsultationResponse,
     * [1]=ProfessionalSpecialist, [2]=Demographic, [3]=Provider.
     *
     * @param items Object[] the JPA projection row
     * @return ConsultationResponseSearchResult the populated DTO
     */
    private ConsultationResponseSearchResult convertToResponseSearchResult(Object[] items) {
        ConsultationResponseSearchResult result = new ConsultationResponseSearchResult();

        ConsultationResponse consultResponse = (ConsultationResponse) items[0];
        ProfessionalSpecialist professionalSpecialist = (ProfessionalSpecialist) items[1];
        Demographic demographic = (Demographic) items[2];
        Provider provider = (Provider) items[3];


        result.setAppointmentDate(joinDateAndTime(consultResponse.getAppointmentDate(), consultResponse.getAppointmentTime()));
        result.setReferringDoctor(professionalSpecialist);
        result.setDemographic(demographic);
        result.setId(consultResponse.getId());
        result.setLastFollowUp(consultResponse.getFollowUpDate());
        result.setProvider(provider);
        result.setReferralDate(consultResponse.getReferralDate());
        result.setResponseDate(consultResponse.getResponseDate());
        result.setStatus(consultResponse.getStatus());
        result.setUrgency(consultResponse.getUrgency());

        if (consultResponse.getSendTo() != null && !consultResponse.getSendTo().isEmpty() && !consultResponse.getSendTo().equals("-1")) {
            result.setTeamName(consultResponse.getSendTo());
        }

        return result;
    }

    /**
     * Merges date-only and time-only Date values into a single Date. The legacy schema
     * stores appointment date and time in separate columns; this combines them.
     *
     * @param date Date the date component (year, month, day)
     * @param time Date the time component (hour, minute, second)
     * @return Date the combined date-time, or null if either input is null
     */
    private Date joinDateAndTime(Date date, Date time) {

        if (date == null || time == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        Calendar timeCal = Calendar.getInstance();
        timeCal.setTime(time);

        cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
        cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
        cal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND));

        return cal.getTime();
    }

    /**
     * Verifies the current user has the specified privilege on the consultation
     * security object ("_con"). Throws RuntimeException if access is denied.
     *
     * @param loggedInInfo LoggedInInfo the current user session
     * @param privilege String the privilege level (SecurityInfoManager.READ, WRITE, etc.)
     */
    private void checkPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", privilege, null)) {
            throw new RuntimeException("Access Denied");
        }
    }

    @Override
    public List<ProfessionalSpecialist> findByService(LoggedInInfo loggedInInfo, String serviceName) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        List<ProfessionalSpecialist> results = professionalSpecialistDao.findByService(serviceName);

        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.findByService", "serviceName" + serviceName);


        return results;
    }

    @Override
    public List<ProfessionalSpecialist> findByServiceId(LoggedInInfo loggedInInfo, Integer serviceId) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        List<ProfessionalSpecialist> results = professionalSpecialistDao.findByServiceId(serviceId);

        LogAction.addLogSynchronous(loggedInInfo, "ConsultationManager.findByServiceId", "serviceId" + serviceId);


        return results;
    }

    @Override
    public List<ConsultDocs> getAttachedDocumentsByType(LoggedInInfo loggedInInfo, Integer consultRequestId, String docType) {
        return consultDocsDao.findByRequestIdDocType(consultRequestId, docType);
    }

    @Override
    public Path renderConsultationForm(HttpServletRequest request) throws PDFGenerationException {
        Path path = null;
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();) {
            ConsultationPDFCreator consultationPDFCreator = new ConsultationPDFCreator(request, outputStream);
            consultationPDFCreator.printPdf(loggedInInfo);
            path = nioFileManager.saveTempFile("temporaryPDF" + new Date().getTime(), outputStream);
        } catch (IOException | DocumentException e) {
            throw new PDFGenerationException("An error occurred while creating the pdf of the consultation request", e);
        }
        return path;
    }

    @Override
    public List<EctFormData.PatientForm> getAttachedForms(LoggedInInfo loggedInInfo, int consultRequestId, int demographicNo) {
        List<ConsultDocs> attachedForms = getAttachedDocumentsByType(loggedInInfo, consultRequestId, ConsultDocs.DOCTYPE_FORM);
        List<EctFormData.PatientForm> filteredForms = new ArrayList<>(attachedForms.size());
        /*
         * Sure wish we didn't have to do this.  It's the only option without having to refactor a
         * whole string of dated code.
         */
        List<EctFormData.PatientForm> allForms = formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, true, true);
        for (ConsultDocs attached : attachedForms) {
            for (EctFormData.PatientForm form : allForms) {
                if ((form.getFormId()).equals((attached.getDocumentNo() + ""))) {
                    filteredForms.add(form);
                    break;
                }
            }
        }

        return filteredForms;
    }

    @Override
    public List<EFormData> getAttachedEForms(String requestId) {
        return EFormUtil.listPatientEformsCurrentAttachedToConsult(requestId);
    }

    @Override
    public ArrayList<HashMap<String, ? extends Object>> getAttachedHRMDocuments(LoggedInInfo loggedInInfo, String demographicNo, String requestId) {
        List<ConsultDocs> attachedHRMDocuments = getAttachedDocumentsByType(loggedInInfo, Integer.parseInt(requestId), ConsultDocs.DOCTYPE_HRM);
        //TODO: refactor HRMUtil so that it's possible to call a function, pass in a particular HRM ID, and return the same information for that HRM that listHRMDocuments does
        //		once this is done, would be possible to simply iterate over attachedHRMDocuments
        //		In the absence of the above refactoring, the following gets the full listHRMDocuments and then filters for only the items that are actually attached to the consult
        ArrayList<HashMap<String, ? extends Object>> allHRMDocuments = HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, demographicNo, false);
        ArrayList<HashMap<String, ? extends Object>> filteredHRMDocuments = new ArrayList<>(attachedHRMDocuments.size());
        for (ConsultDocs attachedHRMDocument : attachedHRMDocuments) {
            for (HashMap<String, ? extends Object> hrmDocument : allHRMDocuments) {
                if (((Integer) hrmDocument.get("id")) == attachedHRMDocument.getDocumentNo()) {
                    filteredHRMDocuments.add(hrmDocument);
                }
            }
        }
        //return the subset of listHRMDocuments that is attached
        return filteredHRMDocuments;
    }

    @Override
    public void archiveConsultationRequest(Integer requestId) {
        ConsultationRequest c = consultationRequestDao.find(requestId);
        if (c != null) {
            List<ConsultationRequestExt> exts = consultationRequestExtDao.getConsultationRequestExts(requestId);

            ConsultationRequestArchive a = new ConsultationRequestArchive();
            a.setAllergies(c.getAllergies());
            a.setAppointmentDate(c.getAppointmentDate());
            a.setAppointmentInstructions(c.getAppointmentInstructions());
            a.setAppointmentTime(c.getAppointmentTime());
            a.setClinicalInfo(c.getClinicalInfo());
            a.setConcurrentProblems(c.getConcurrentProblems());
            a.setCurrentMeds(c.getCurrentMeds());
            a.setDemographicId(c.getDemographicId());
            a.setFdid(c.getFdid());
            a.setFollowUpDate(c.getFollowUpDate());
            a.setLastUpdateDate(c.getLastUpdateDate());
            a.setLetterheadAddress(c.getLetterheadAddress());
            a.setLetterheadFax(c.getLetterheadFax());
            a.setLetterheadName(c.getLetterheadName());
            a.setLetterheadPhone(c.getLetterheadPhone());
            a.setLookupListItem(c.getLookupListItem());
            a.setPatientWillBook(c.isPatientWillBook());
            //	a.setProfessionalSpecialist(c.getProfessionalSpecialist());
            a.setProviderNo(c.getProviderNo());
            a.setReasonForReferral(c.getReasonForReferral());
            a.setReferralDate(c.getReferralDate());
            a.setRequestId(requestId);
            a.setSendTo(c.getSendTo());
            a.setServiceId(c.getServiceId());
            a.setSignatureImg(c.getSignatureImg());
            a.setSiteName(c.getSiteName());
            a.setSource(c.getSource());
            a.setStatus(c.getStatus());
            a.setStatusText(c.getStatusText());
            a.setUrgency(c.getUrgency());


            consultationRequestArchiveDao.persist(a);

            if (c.getProfessionalSpecialist() != null) {
                ProfessionalSpecialist professionalSpecialist = professionalSpecialistDao.find(c.getProfessionalSpecialist().getId());
                if (professionalSpecialist != null) {
                    a.setProfessionalSpecialist(professionalSpecialist);
                    consultationRequestArchiveDao.merge(a);
                }
            }


            //List<ConsultationRequestExtArchive> aExts = new ArrayList<ConsultationRequestExtArchive>();
            for (ConsultationRequestExt e : exts) {
                ConsultationRequestExtArchive aext = new ConsultationRequestExtArchive();
                aext.setDateCreated(e.getDateCreated());
                aext.setKey(e.getKey());
                aext.setOriginalId(e.getId());
                aext.setRequestId(requestId);
                aext.setValue(e.getValue());
                aext.setConsultationRequestArchiveId(a.getId());
                //aExts.add(aext);

                consultationRequestExtArchiveDao.persist(aext);
            }
        }
    }

    /**
     * Saves or updates consultation request extras depending on if the key already exists in the table
     *
     * @param requestId The id of the consultation request the extras are linked to
     * @param extras    A list of extras to save or update
     */
    @Override
    public void saveOrUpdateExts(int requestId, List<ConsultationRequestExt> extras) {
        List<ConsultationRequestExt> existingExtras = consultationRequestExtDao.getConsultationRequestExts(requestId);
        Map<String, ConsultationRequestExt> extraMap = getExtsAsMap(existingExtras);
        List<ConsultationRequestExt> newExtras = new ArrayList<>();

        for (ConsultationRequestExt extra : extras) {
            extra.setRequestId(requestId);

            // If the map contains the key then the extra already exists and will be updated, else saves a new one
            ConsultationRequestExt savedExtra = extraMap.get(extra.getKey());
            if (savedExtra != null) {
                // If the value isn't the same, update it
                if (!savedExtra.getValue().equals(extra.getValue())) {
                    savedExtra.setValue(extra.getValue());
                    consultationRequestExtDao.merge(savedExtra);
                }
            } else {
                extra.setDateCreated(new Date());
                newExtras.add(extra);
            }
        }

        // If there are new extras, batch persists them
        if (!newExtras.isEmpty()) {
            consultationRequestExtDao.batchPersist(newExtras);
        }
    }

    @Override
    public Map<String, ConsultationRequestExt> getExtsAsMap(List<ConsultationRequestExt> extras) {
        Map<String, ConsultationRequestExt> extraMap = new HashMap<>();

        for (ConsultationRequestExt extra : extras) {
            extraMap.put(extra.getKey(), extra);
        }

        return extraMap;
    }

    @Override
    public Map<String, String> getExtValuesAsMap(List<ConsultationRequestExt> extras) {
        Map<String, String> extraMap = new HashMap<>();

        for (ConsultationRequestExt extra : extras) {
            extraMap.put(extra.getKey(), extra.getValue());
        }

        return extraMap;
    }
}
