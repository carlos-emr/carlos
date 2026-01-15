//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.managers;

import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.itextpdf.text.DocumentException;
import ca.openosp.openo.commn.model.ConsultDocs;
import ca.openosp.openo.commn.model.ConsultResponseDoc;
import ca.openosp.openo.commn.model.ConsultationRequest;
import ca.openosp.openo.commn.model.ConsultationRequestExt;
import ca.openosp.openo.commn.model.ConsultationResponse;
import ca.openosp.openo.commn.model.ConsultationServices;
import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.commn.model.EFormData;
import ca.openosp.openo.commn.model.ProfessionalSpecialist;
import ca.openosp.openo.consultations.ConsultationRequestSearchFilter;
import ca.openosp.openo.consultations.ConsultationResponseSearchFilter;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.PDFGenerationException;
import ca.openosp.openo.webserv.rest.to.model.ConsultationAttachment;
import ca.openosp.openo.webserv.rest.to.model.ConsultationRequestSearchResult;
import ca.openosp.openo.webserv.rest.to.model.ConsultationResponseSearchResult;
import ca.openosp.openo.webserv.rest.to.model.OtnEconsult;

import ca.uhn.hl7v2.HL7Exception;
import ca.openosp.openo.encounter.data.EctFormData;

public interface ConsultationManager {

    public final String CON_REQUEST_ENABLED = "consultRequestEnabled";
    public final String CON_RESPONSE_ENABLED = "consultResponseEnabled";
    public final String ENABLED_YES = "Y";

    public List<ConsultationRequestSearchResult> search(LoggedInInfo loggedInInfo, ConsultationRequestSearchFilter filter);

    public List<ConsultationResponseSearchResult> search(LoggedInInfo loggedInInfo, ConsultationResponseSearchFilter filter);

    public int getConsultationCount(ConsultationRequestSearchFilter filter);

    public int getConsultationCount(ConsultationResponseSearchFilter filter);

    public boolean hasOutstandingConsultations(LoggedInInfo loggedInInfo, Integer demographicNo);

    public ConsultationRequest getRequest(LoggedInInfo loggedInInfo, Integer id);

    public ConsultationResponse getResponse(LoggedInInfo loggedInInfo, Integer id);

    public List<ConsultationServices> getConsultationServices();

    public List<ProfessionalSpecialist> getReferringDoctorList();

    public ProfessionalSpecialist getProfessionalSpecialist(Integer id);

    public void saveConsultationRequest(LoggedInInfo loggedInInfo, ConsultationRequest request);

    public void saveConsultationResponse(LoggedInInfo loggedInInfo, ConsultationResponse response);

    public List<ConsultDocs> getConsultRequestDocs(LoggedInInfo loggedInInfo, Integer requestId);

    public List<ConsultResponseDoc> getConsultResponseDocs(LoggedInInfo loggedInInfo, Integer responseId);

    public void saveConsultRequestDoc(LoggedInInfo loggedInInfo, ConsultDocs doc);

    public void saveConsultResponseDoc(LoggedInInfo loggedInInfo, ConsultResponseDoc doc);

    public void enableConsultRequestResponse(boolean conRequest, boolean conResponse);

    public boolean isConsultRequestEnabled();

    public boolean isConsultResponseEnabled();

    public void doHl7Send(LoggedInInfo loggedInInfo, Integer consultationRequestId) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, IOException, HL7Exception, ServletException, DocumentException;

    public void importEconsult(LoggedInInfo loggedInInfo, OtnEconsult otnEconsult) throws Exception;

    public List<Document> getEconsultDocuments(LoggedInInfo loggedInInfo, int demographicNo);

    public List<ConsultationAttachment> getEReferAttachments(LoggedInInfo loggedInInfo, HttpServletRequest request, HttpServletResponse response, Integer demographicNo) throws PDFGenerationException;

    public List<ProfessionalSpecialist> findByService(LoggedInInfo loggedInInfo, String serviceName);

    public List<ProfessionalSpecialist> findByServiceId(LoggedInInfo loggedInInfo, Integer serviceId);

    public List<ConsultDocs> getAttachedDocumentsByType(LoggedInInfo loggedInInfo, Integer consultRequestId, String docType);

    public Path renderConsultationForm(HttpServletRequest request) throws PDFGenerationException;

    public List<EctFormData.PatientForm> getAttachedForms(LoggedInInfo loggedInInfo, int consultRequestId, int demographicNo);

    public List<EFormData> getAttachedEForms(String requestId);

    public ArrayList<HashMap<String, ? extends Object>> getAttachedHRMDocuments(LoggedInInfo loggedInInfo, String demographicNo, String requestId);

    public void archiveConsultationRequest(Integer requestId);

    public void saveOrUpdateExts(int requestId, List<ConsultationRequestExt> extras);

    public Map<String, ConsultationRequestExt> getExtsAsMap(List<ConsultationRequestExt> extras);

    public Map<String, String> getExtValuesAsMap(List<ConsultationRequestExt> extras);
}
