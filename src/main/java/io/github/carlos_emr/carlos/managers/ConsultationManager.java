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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import io.github.carlos_emr.carlos.commn.model.ConsultationResponse;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.consultations.ConsultationRequestSearchFilter;
import io.github.carlos_emr.carlos.consultations.ConsultationResponseSearchFilter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationAttachment;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestSearchResult;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationResponseSearchResult;
import io.github.carlos_emr.carlos.webserv.rest.to.model.OtnEconsult;

import io.github.carlos_emr.carlos.encounter.data.EctFormData;

/**
 * Service interface for managing consultation requests and responses in CARLOS EMR.
 *
 * <p>Provides operations for the complete consultation lifecycle:
 * <ul>
 *   <li><strong>Search and retrieval</strong> -- Finding consultation requests/responses
 *       with filtering, counting, and outstanding consultation detection</li>
 *   <li><strong>CRUD operations</strong> -- Creating, saving, and archiving consultation
 *       requests and responses with associated documents</li>
 *   <li><strong>HL7 electronic referral</strong> -- Sending consultation requests
 *       electronically via HL7 REF_I12 messages with attached documents and labs</li>
 *   <li><strong>eConsult/eReferral</strong> -- Importing OTN eConsults and preparing
 *       attachments for eReferral submissions</li>
 *   <li><strong>Attachment management</strong> -- Retrieving attached documents, forms,
 *       eForms, and HRM documents for a consultation request</li>
 *   <li><strong>PDF generation</strong> -- Rendering consultation forms as PDF using OpenPDF</li>
 *   <li><strong>Extension management</strong> -- Saving and querying consultation request
 *       extension key-value pairs</li>
 * </ul>
 *
 * <p>All methods that access patient data require a {@link LoggedInInfo} parameter
 * and enforce security privilege checks via {@link SecurityInfoManager}.
 *
 * @see ConsultationManagerImpl
 * @see ConsultationRequest
 * @see ConsultationResponse
 * @since 2014 (McMaster University)
 */
public interface ConsultationManager {

    /** Property name for enabling/disabling consultation requests. */
    public final String CON_REQUEST_ENABLED = "consultRequestEnabled";
    /** Property name for enabling/disabling consultation responses. */
    public final String CON_RESPONSE_ENABLED = "consultResponseEnabled";
    /** Value indicating a consultation feature is enabled. */
    public final String ENABLED_YES = "Y";

    /**
     * Searches for consultation requests matching the given filter criteria.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param filter ConsultationRequestSearchFilter the search criteria
     * @return List of ConsultationRequestSearchResult matching results
     */
    public List<ConsultationRequestSearchResult> search(LoggedInInfo loggedInInfo, ConsultationRequestSearchFilter filter);

    /**
     * Searches for consultation responses matching the given filter criteria.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param filter ConsultationResponseSearchFilter the search criteria
     * @return List of ConsultationResponseSearchResult matching results
     */
    public List<ConsultationResponseSearchResult> search(LoggedInInfo loggedInInfo, ConsultationResponseSearchFilter filter);

    /**
     * Returns the total count of consultation requests matching the filter.
     *
     * @param filter ConsultationRequestSearchFilter the search criteria
     * @return int the count of matching requests
     */
    public int getConsultationCount(ConsultationRequestSearchFilter filter);

    /**
     * Returns the total count of consultation responses matching the filter.
     *
     * @param filter ConsultationResponseSearchFilter the search criteria
     * @return int the count of matching responses
     */
    public int getConsultationCount(ConsultationResponseSearchFilter filter);

    /**
     * Checks whether a patient has incomplete consultation requests older than one month.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param demographicNo Integer the patient demographic number
     * @return boolean true if outstanding consultations exist
     */
    public boolean hasOutstandingConsultations(LoggedInInfo loggedInInfo, Integer demographicNo);

    /**
     * Retrieves a consultation request by its ID.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param id Integer the consultation request ID
     * @return ConsultationRequest the request, or {@code null} if not found
     */
    public ConsultationRequest getRequest(LoggedInInfo loggedInInfo, Integer id);

    /**
     * Retrieves a consultation response by its ID.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param id Integer the consultation response ID
     * @return ConsultationResponse the response, or {@code null} if not found
     */
    public ConsultationResponse getResponse(LoggedInInfo loggedInInfo, Integer id);

    /**
     * Returns all active consultation services, excluding the "Referring Doctor" service.
     *
     * @return List of ConsultationServices active services
     */
    public List<ConsultationServices> getConsultationServices();

    /**
     * Returns the list of referring doctors from the "Referring Doctor" service.
     *
     * @return List of ProfessionalSpecialist referring doctors, or {@code null} if the service does not exist
     */
    public List<ProfessionalSpecialist> getReferringDoctorList();

    /**
     * Retrieves a professional specialist by ID.
     *
     * @param id Integer the specialist ID
     * @return ProfessionalSpecialist the specialist record
     */
    public ProfessionalSpecialist getProfessionalSpecialist(Integer id);

    /**
     * Saves or updates a consultation request and its associated extension data.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param request ConsultationRequest the request to save
     */
    public void saveConsultationRequest(LoggedInInfo loggedInInfo, ConsultationRequest request);

    /**
     * Saves or updates a consultation response.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param response ConsultationResponse the response to save
     */
    public void saveConsultationResponse(LoggedInInfo loggedInInfo, ConsultationResponse response);

    /**
     * Retrieves all documents attached to a consultation request.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param requestId Integer the consultation request ID
     * @return List of ConsultDocs attached documents
     */
    public List<ConsultDocs> getConsultRequestDocs(LoggedInInfo loggedInInfo, Integer requestId);

    /**
     * Retrieves all documents attached to a consultation response.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param responseId Integer the consultation response ID
     * @return List of ConsultResponseDoc attached documents
     */
    public List<ConsultResponseDoc> getConsultResponseDocs(LoggedInInfo loggedInInfo, Integer responseId);

    /**
     * Saves or updates a consultation request document attachment.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param doc ConsultDocs the document attachment to save
     */
    public void saveConsultRequestDoc(LoggedInInfo loggedInInfo, ConsultDocs doc);

    /**
     * Saves or updates a consultation response document attachment.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param doc ConsultResponseDoc the document attachment to save
     */
    public void saveConsultResponseDoc(LoggedInInfo loggedInInfo, ConsultResponseDoc doc);

    /**
     * Enables or disables the consultation request and response features system-wide.
     *
     * @param conRequest boolean true to enable consultation requests
     * @param conResponse boolean true to enable consultation responses
     */
    public void enableConsultRequestResponse(boolean conRequest, boolean conResponse);

    /**
     * Checks whether consultation requests are enabled system-wide.
     *
     * @return boolean true if consultation requests are enabled
     */
    public boolean isConsultRequestEnabled();

    /**
     * Checks whether consultation responses are enabled system-wide.
     *
     * @return boolean true if consultation responses are enabled
     */
    public boolean isConsultResponseEnabled();

    /**
     * Imports an OTN eConsult as a document attached to the specified demographic.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param otnEconsult OtnEconsult the eConsult data to import
     * @throws Exception if an error occurs during import or document save
     */
    public void importEconsult(LoggedInInfo loggedInInfo, OtnEconsult otnEconsult) throws Exception;

    /**
     * Retrieves all eConsult documents for a patient demographic.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param demographicNo int the patient demographic number
     * @return List of Document eConsult documents
     */
    public List<Document> getEconsultDocuments(LoggedInInfo loggedInInfo, int demographicNo);

    /**
     * Retrieves prepared eReferral attachments for a demographic, limited to
     * those prepared within the past hour. Archives the attachments after retrieval.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param request HttpServletRequest for form rendering
     * @param response HttpServletResponse for form rendering
     * @param demographicNo Integer the patient demographic number
     * @return List of ConsultationAttachment containing file data and metadata
     * @throws PDFGenerationException if an error occurs generating a PDF attachment
     */
    public List<ConsultationAttachment> getEReferAttachments(LoggedInInfo loggedInInfo, HttpServletRequest request, HttpServletResponse response, Integer demographicNo) throws PDFGenerationException;

    /**
     * Finds professional specialists associated with a named consultation service.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param serviceName String the consultation service name
     * @return List of ProfessionalSpecialist matching specialists
     */
    public List<ProfessionalSpecialist> findByService(LoggedInInfo loggedInInfo, String serviceName);

    /**
     * Finds professional specialists associated with a consultation service by ID.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param serviceId Integer the consultation service ID
     * @return List of ProfessionalSpecialist matching specialists
     */
    public List<ProfessionalSpecialist> findByServiceId(LoggedInInfo loggedInInfo, Integer serviceId);

    /**
     * Retrieves attached documents of a specific type for a consultation request.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param consultRequestId Integer the consultation request ID
     * @param docType String the document type code (e.g., {@link ConsultDocs#DOCTYPE_DOC})
     * @return List of ConsultDocs matching attached documents
     */
    public List<ConsultDocs> getAttachedDocumentsByType(LoggedInInfo loggedInInfo, Integer consultRequestId, String docType);

    /**
     * Renders a consultation request form as a PDF document.
     *
     * @param request HttpServletRequest containing the consultation form parameters
     * @return Path to the generated temporary PDF file
     * @throws PDFGenerationException if an error occurs during PDF generation
     */
    public Path renderConsultationForm(HttpServletRequest request) throws PDFGenerationException;

    /**
     * Retrieves patient forms attached to a consultation request.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param consultRequestId int the consultation request ID
     * @param demographicNo int the patient demographic number
     * @return List of PatientForm attached forms
     */
    public List<EctFormData.PatientForm> getAttachedForms(LoggedInInfo loggedInInfo, int consultRequestId, int demographicNo);

    /**
     * Retrieves eForms currently attached to a consultation request.
     *
     * @param requestId String the consultation request ID
     * @return List of EFormData attached eForms
     */
    public List<EFormData> getAttachedEForms(String requestId);

    /**
     * Retrieves HRM (Hospital Report Manager) documents attached to a consultation request.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info
     * @param demographicNo String the patient demographic number
     * @param requestId String the consultation request ID
     * @return ArrayList of HashMap containing HRM document metadata
     */
    public ArrayList<HashMap<String, ? extends Object>> getAttachedHRMDocuments(LoggedInInfo loggedInInfo, String demographicNo, String requestId);

    /**
     * Archives a consultation request by copying it and its extensions to archive tables.
     *
     * @param requestId Integer the consultation request ID to archive
     */
    public void archiveConsultationRequest(Integer requestId);

    /**
     * Saves or updates consultation request extension key-value pairs.
     * Existing keys are updated; new keys are batch-persisted.
     *
     * @param requestId int the consultation request ID
     * @param extras List of ConsultationRequestExt the extensions to save or update
     */
    public void saveOrUpdateExts(int requestId, List<ConsultationRequestExt> extras);

    /**
     * Converts a list of consultation request extensions to a map keyed by extension key.
     *
     * @param extras List of ConsultationRequestExt the extensions to convert
     * @return Map mapping extension key to the ConsultationRequestExt object
     */
    public Map<String, ConsultationRequestExt> getExtsAsMap(List<ConsultationRequestExt> extras);

    /**
     * Converts a list of consultation request extensions to a map of key-value String pairs.
     *
     * @param extras List of ConsultationRequestExt the extensions to convert
     * @return Map mapping extension key to its String value
     */
    public Map<String, String> getExtValuesAsMap(List<ConsultationRequestExt> extras);
}
