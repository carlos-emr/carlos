/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationAttachmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regression tests for consultation REST attachment error handling.
 *
 * @since 2026-05-26
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultationWebService regression tests")
@Tag("unit")
@Tag("rest")
@Tag("regression")
class ConsultationWebServiceRegressionTest extends io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase {

    private static final Integer DEMOGRAPHIC_NO = 123;
    private static final String PROVIDER_NO = "999998";
    private static final byte[] FILE_CONTENTS = "document body".getBytes(StandardCharsets.UTF_8);

    @Mock
    private DocumentManager documentManager;

    @Mock
    private ConsultationManager consultationManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    @Mock
    private io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao routing;

    @Mock
    private io.github.carlos_emr.carlos.managers.SecurityInfoManager attachmentSecurity;
    @Mock
    private io.github.carlos_emr.carlos.commn.dao.DocumentDao attachmentDocuments;
    @Mock
    private io.github.carlos_emr.carlos.commn.dao.EFormDataDao attachmentEforms;
    @Mock
    private io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao attachmentHrms;
    @Mock
    private io.github.carlos_emr.carlos.managers.FormsManager attachmentForms;
    @Mock
    private io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao requestDao;
    @Mock
    private io.github.carlos_emr.carlos.commn.dao.ConsultResponseDao responseDao;

    private ConsultationWebService service;

    @BeforeEach
    void setUp() {
        service = new ConsultationWebService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        ReflectionTestUtils.setField(service, "attachmentSelectionAccess",
                new io.github.carlos_emr.carlos.documentManager.AttachmentSelectionAccess(
                        attachmentSecurity, attachmentDocuments, attachmentEforms, attachmentHrms, attachmentForms));
        ReflectionTestUtils.setField(service, "consultationRequestDao", requestDao);
        ReflectionTestUtils.setField(service, "consultationResponseDao", responseDao);
        org.mockito.Mockito.lenient().when(attachmentSecurity.hasPrivilege(eq(loggedInInfo), any(), eq("r"), eq("123"))).thenReturn(true);
        org.mockito.Mockito.lenient().when(requestDao.lockForAttachmentSync(456)).thenReturn(storedRequest());
        org.mockito.Mockito.lenient().when(responseDao.lockForAttachmentSync(456)).thenReturn(storedResponse());
        ReflectionTestUtils.setField(service, "patientLabRoutingDao", routing);
        ReflectionTestUtils.setField(service, "documentManager", documentManager);
        ReflectionTestUtils.setField(service, "consultationManager", consultationManager);
    }

    @Test
    @DisplayName("should return invalid filename attachment with validation error without propagating exception")
    void shouldReturnInvalidFilenameAttachment_withValidationErrorWithoutPropagatingException() throws Exception {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        request.setAttachments(List.of(newDocumentAttachment()));
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(DEMOGRAPHIC_NO),
                eq(PROVIDER_NO), eq(FILE_CONTENTS)))
                .thenThrow(new IOException("Document filename failed path validation",
                        new FileValidationException("unsafe filename ../secret.pdf")));
        when(consultationManager.getConsultRequestDocs(loggedInInfo, request.getId())).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);

        assertThat(request.getAttachments()).hasSize(1);
        assertThat(request.getAttachments().get(0).getValidationError()).isEqualTo("Invalid attachment filename");
        assertThat(request.getAttachments().get(0).getDocumentNo()).isZero();
    }

    @Test
    void shouldKeepBothSources_whenRestLabNumbersOverlap() {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        request.setAttachments(List.of(lab("HL7"), lab("MDS"), lab("HL7")));
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of());
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        route("HL7");
        route("MDS");

        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);

        var saved = org.mockito.ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.ConsultDocs.class);
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.times(2)).saveConsultRequestDoc(eq(loggedInInfo), saved.capture());
        assertThat(saved.getAllValues()).extracting(io.github.carlos_emr.carlos.commn.model.ConsultDocs::getLabType)
                .containsExactly("HL7", "MDS");
    }

    @Test
    void shouldPreserveExistingLab_whenRestSelectionRemainsAttached() {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        request.setAttachments(List.of(lab("MDS")));
        var existing = new io.github.carlos_emr.carlos.commn.model.ConsultDocs(456, 77, "L", PROVIDER_NO);
        existing.setId(900);
        existing.setLabType("MDS");
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of(existing));
        route("MDS");

        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);

        assertThat(existing.getDeleted()).isNull();
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultRequestDoc(any(), any());
    }

    @Test
    void shouldRejectAmbiguousRestSelectionBeforeDetach_whenLegacyClientOmitsSource() {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        request.setAttachments(List.of(lab(null)));
        var existing = new io.github.carlos_emr.carlos.commn.model.ConsultDocs(456, 77, "L", PROVIDER_NO);
        existing.setId(900);
        existing.setLabType("MDS");
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of(existing));
        when(routing.findLabSourcesForPatient(77, DEMOGRAPHIC_NO)).thenReturn(List.of("HL7", "MDS"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(existing.getDeleted()).isNull();
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultRequestDoc(any(), any());
    }

    @Test
    void shouldExposeNoViewerUrl_whenLegacyLabSourceIsUnresolved() {
        var lab = new io.github.carlos_emr.carlos.lab.ca.on.LabResultData();
        lab.labType = "UNRESOLVED";
        lab.setSegmentID("77");
        lab.setLabel("Source confirmation required");
        lab.setAttachmentUnavailable(true);
        var output = new java.util.ArrayList<ConsultationAttachmentTo1>();
        ReflectionTestUtils.invokeMethod(service, "getLabs", List.of(lab), "123", true, output);
        assertThat(output).hasSize(1);
        assertThat(output.getFirst().getLabType()).isEqualTo("UNRESOLVED");
        assertThat(output.getFirst().getUrl()).isNull();
        assertThat(output.getFirst().getDisplayName()).isEqualTo("Source confirmation required");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldUseStoredPatient_whenResponseMetadataOmitsOrMatchesPatient(boolean omitted) {
        var stored = storedResponse();
        var submitted = responseSubmission(omitted ? null : DEMOGRAPHIC_NO);
        when(consultationManager.getResponse(loggedInInfo, 456)).thenReturn(stored);
        when(consultationManager.getConsultResponseDocs(loggedInInfo, 456)).thenReturn(List.of());
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        route("MDS");
        var transaction = responseTransaction();

        assertThat(service.saveResponse(submitted)).isSameAs(submitted);

        assertThat(stored.getDemographicNo()).isEqualTo(DEMOGRAPHIC_NO);
        assertThat(stored.getPlan()).isEqualTo("updated plan");
        org.mockito.Mockito.verify(consultationManager).saveConsultationResponse(loggedInInfo, stored);
        var saved = org.mockito.ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc.class);
        org.mockito.Mockito.verify(consultationManager).saveConsultResponseDoc(eq(loggedInInfo), saved.capture());
        assertThat(saved.getValue().getLabType()).isEqualTo("MDS");
        org.mockito.Mockito.verify(transaction).commit(any());
        org.mockito.Mockito.verify(transaction, org.mockito.Mockito.never()).rollback(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {124, 0, -1})
    void shouldRejectResponsePatientChange_beforeMutatingStoredClinicalData(int patient) {
        var stored = storedResponse();
        when(consultationManager.getResponse(loggedInInfo, 456)).thenReturn(stored);
        var transaction = responseTransaction();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveResponse(responseSubmission(patient)))
                .isInstanceOf(jakarta.ws.rs.BadRequestException.class);

        assertThat(stored.getDemographicNo()).isEqualTo(DEMOGRAPHIC_NO);
        assertThat(stored.getPlan()).isEqualTo("original plan");
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultationResponse(any(), any());
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultResponseDoc(any(), any());
        org.mockito.Mockito.verifyNoInteractions(routing);
        org.mockito.Mockito.verify(transaction).rollback(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missingResponse", "missingStoredPatient", "missingReferringDoctor", "newMissingPatient", "missingPayload"})
    void shouldRejectInvalidResponseMetadata_withoutFalseSuccessOrPartialMutation(String condition) {
        var stored = storedResponse();
        var submitted = responseSubmission(DEMOGRAPHIC_NO);
        if ("missingStoredPatient".equals(condition)) stored.setDemographicNo(null);
        if ("missingReferringDoctor".equals(condition)) submitted.setReferringDoctor(null);
        if ("newMissingPatient".equals(condition)) {
            submitted.setId(null);
            submitted.setDemographic(null);
        } else if (!"missingPayload".equals(condition)) {
            when(consultationManager.getResponse(loggedInInfo, 456))
                    .thenReturn("missingResponse".equals(condition) ? null : stored);
        }
        var transaction = responseTransaction();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveResponse("missingPayload".equals(condition) ? null : submitted))
                .isInstanceOf(jakarta.ws.rs.BadRequestException.class);
        assertThat(stored.getPlan()).isEqualTo("original plan");
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultationResponse(any(), any());
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultResponseDoc(any(), any());
        org.mockito.Mockito.verifyNoInteractions(routing);
        org.mockito.Mockito.verify(transaction).rollback(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldSaveEachSourceOnce_whenResponseSelectionContainsDuplicateLabs(boolean alreadyAttached) {
        var stored = storedResponse();
        var submitted = responseSubmission(DEMOGRAPHIC_NO);
        submitted.setAttachments(List.of(lab("MDS"), lab("HL7"), lab("MDS")));
        when(consultationManager.getResponse(loggedInInfo, 456)).thenReturn(stored);
        var existing = new io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc(456, 77, "L", PROVIDER_NO);
        ReflectionTestUtils.setField(existing, "id", 900);
        existing.setLabType("MDS");
        when(consultationManager.getConsultResponseDocs(loggedInInfo, 456)).thenReturn(alreadyAttached ? List.of(existing) : List.of());
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        route("MDS");
        route("HL7");
        responseTransaction();

        service.saveResponse(submitted);

        var saved = org.mockito.ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc.class);
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.times(alreadyAttached ? 1 : 2)).saveConsultResponseDoc(eq(loggedInInfo), saved.capture());
        assertThat(saved.getAllValues()).extracting(io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc::getLabType)
                .containsExactlyElementsOf(alreadyAttached ? List.of("HL7") : List.of("MDS", "HL7"));
        assertThat(existing.getDeleted()).isNull();
    }

    private io.github.carlos_emr.carlos.commn.model.ConsultationResponse storedResponse() {
        var response = new io.github.carlos_emr.carlos.commn.model.ConsultationResponse();
        ReflectionTestUtils.setField(response, "id", 456);
        response.setDemographicNo(DEMOGRAPHIC_NO);
        response.setPlan("original plan");
        return response;
    }

    private io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationResponseTo1 responseSubmission(Integer patient) {
        var response = new io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationResponseTo1();
        response.setId(456);
        if (patient != null) {
            var demographic = new io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1();
            demographic.setDemographicNo(patient);
            response.setDemographic(demographic);
        }
        var referring = new io.github.carlos_emr.carlos.webserv.rest.to.model.ProfessionalSpecialistTo1();
        referring.setId(7);
        response.setReferringDoctor(referring);
        response.setPlan("updated plan");
        response.setAttachments(List.of(lab("MDS")));
        return response;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldRejectRequestBeforeMutation_whenPatientChangesOrRequestIsMissing(boolean missing) {
        var submitted = requestSubmission();
        var stored = storedRequest();
        if (!missing) submitted.setDemographicId(999);
        var demographicManager = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.DemographicManager.class);
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);
        when(demographicManager.getDemographic(loggedInInfo, submitted.getDemographicId()))
                .thenReturn(new io.github.carlos_emr.carlos.commn.model.Demographic());
        when(consultationManager.getRequest(loggedInInfo, 456)).thenReturn(missing ? null : stored);
        var transaction = responseTransaction();

        assertThat(service.updateConsultation(submitted).getStatus()).isEqualTo(400);

        assertThat(stored.getDemographicId()).isEqualTo(DEMOGRAPHIC_NO);
        assertThat(stored.getReasonForReferral()).isEqualTo("original reason");
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultationRequest(any(), any());
        org.mockito.Mockito.verifyNoInteractions(routing, documentManager);
        org.mockito.Mockito.verify(transaction).rollback(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"omitted", "null", "empty"})
    void shouldRespectAttachmentPresence_whenUpdatingRequest(String selection) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode json = mapper.valueToTree(requestSubmission());
        json.remove("attachments");
        if ("null".equals(selection)) json.putNull("attachments");
        if ("empty".equals(selection)) json.putArray("attachments");
        var submitted = mapper.treeToValue(json, ConsultationRequestTo1.class);
        boolean omitted = !"empty".equals(selection);
        assertThat(submitted.hasAttachmentSelection()).isEqualTo(!"omitted".equals(selection));
        assertThat(mapper.valueToTree(submitted).has("attachmentSelectionProvided")).isFalse();
        assertThat(mapper.valueToTree(submitted).has("attachmentSelection")).isFalse();
        var stored = storedRequest();
        var demographicManager = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.DemographicManager.class);
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);
        when(demographicManager.getDemographic(loggedInInfo, DEMOGRAPHIC_NO))
                .thenReturn(new io.github.carlos_emr.carlos.commn.model.Demographic());
        when(consultationManager.getRequest(loggedInInfo, 456)).thenReturn(stored);
        var existing = new io.github.carlos_emr.carlos.commn.model.ConsultDocs(456, 77, "L", PROVIDER_NO);
        existing.setId(900);
        existing.setLabType("MDS");
        if (!omitted) when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of(existing));
        var transaction = responseTransaction();

        assertThat(service.updateConsultation(submitted).getStatus()).isEqualTo(200);

        assertThat(stored.getReasonForReferral()).isEqualTo("updated reason");
        assertThat(existing.getDeleted()).isEqualTo(omitted ? null : "Y");
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.times(omitted ? 0 : 1)).saveConsultRequestDoc(any(), any());
        org.mockito.Mockito.verify(transaction).commit(any());
    }

    private ConsultationRequestTo1 requestSubmission() {
        var request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        request.setReferralDate(new java.util.Date());
        request.setServiceId(1);
        request.setUrgency("2");
        request.setStatus("1");
        request.setReasonForReferral("updated reason");
        request.setAttachments(List.of(lab("MDS")));
        return request;
    }

    private io.github.carlos_emr.carlos.commn.model.ConsultationRequest storedRequest() {
        var request = new io.github.carlos_emr.carlos.commn.model.ConsultationRequest();
        ReflectionTestUtils.setField(request, "id", 456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        request.setReasonForReferral("original reason");
        return request;
    }

    private org.springframework.transaction.PlatformTransactionManager responseTransaction() {
        var transaction = org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transaction.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        ReflectionTestUtils.setField(service, "transactionManager", transaction);
        return transaction;
    }

    private void route(String source) {
        var row = new io.github.carlos_emr.carlos.commn.model.PatientLabRouting(77, source, DEMOGRAPHIC_NO);
        when(routing.findByLabNoAndLabType(77, source)).thenReturn(List.of(row));
    }

    private ConsultationAttachmentTo1 lab(String source) {
        var attachment = new ConsultationAttachmentTo1(77, "L", true, "Lab", null);
        attachment.setLabType(source);
        return attachment;
    }

    private static ConsultationAttachmentTo1 newDocumentAttachment() {
        ConsultationAttachmentTo1 attachment = new ConsultationAttachmentTo1();
        attachment.setDocumentType(ConsultationAttachmentTo1.TYPE_DOC);
        attachment.setAttached(true);
        attachment.setDocument(validDocument());
        return attachment;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"D,_edoc", "E,_eform", "H,_hrm", "F,_form", "L,_lab"})
    void shouldRejectNewAttachments_whenTypeReadAccessIsMissing(String type, String privilege) {
        when(attachmentSecurity.hasPrivilege(loggedInInfo, privilege, "r", "123")).thenReturn(false);
        assertRejectedSelection(type, SecurityException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"D", "E", "H", "F"})
    void shouldRejectForeignAttachments_beforeAnyRequestOrResponseAttachmentWrite(String type) {
        // The real shared policy sees no patient ownership in any of its DAO results.
        assertRejectedSelection(type, IllegalArgumentException.class);
    }

    private void assertRejectedSelection(String type, Class<? extends Throwable> failure) {
        var selected = new ConsultationAttachmentTo1(78, type, true, "Selected", null);
        if ("L".equals(type)) selected.setLabType("MDS");
        var request = requestSubmission();
        request.setAttachments(List.of(selected));
        var oldRequestDoc = new io.github.carlos_emr.carlos.commn.model.ConsultDocs(456, 77, "D", PROVIDER_NO);
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of(oldRequestDoc));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request))
                .isInstanceOf(failure);
        var response = responseSubmission(null);
        response.setAttachments(List.of(selected));
        var oldResponseDoc = new io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc(456, 77, "D", PROVIDER_NO);
        when(consultationManager.getConsultResponseDocs(loggedInInfo, 456)).thenReturn(List.of(oldResponseDoc));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "saveResponseAttachments", response))
                .isInstanceOf(failure);
        assertThat(oldRequestDoc.getDeleted()).isNull();
        assertThat(oldResponseDoc.getDeleted()).isNull();
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultRequestDoc(any(), any());
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultResponseDoc(any(), any());
        org.mockito.Mockito.verifyNoInteractions(documentManager);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"D,_edoc", "E,_eform", "H,_hrm", "F,_form", "L,_lab"})
    void shouldPreserveUnchangedRestrictedAttachments_withoutOwnershipReadsOrWrites(String type, String privilege) {
        when(attachmentSecurity.hasPrivilege(loggedInInfo, privilege, "r", "123")).thenReturn(false);
        var selected = new ConsultationAttachmentTo1(77, type, true, "Restricted", null);
        if ("L".equals(type)) selected.setLabType("UNRESOLVED");
        var request = requestSubmission();
        request.setAttachments(List.of(selected));
        var oldRequestDoc = new io.github.carlos_emr.carlos.commn.model.ConsultDocs(456, 77, type, PROVIDER_NO);
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of(oldRequestDoc));
        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);
        var response = responseSubmission(null);
        response.setAttachments(List.of(selected));
        var oldResponseDoc = new io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc(456, 77, type, PROVIDER_NO);
        when(consultationManager.getConsultResponseDocs(loggedInInfo, 456)).thenReturn(List.of(oldResponseDoc));
        ReflectionTestUtils.invokeMethod(service, "saveResponseAttachments", response);
        assertThat(oldRequestDoc.getDeleted()).isNull();
        assertThat(oldResponseDoc.getDeleted()).isNull();
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultRequestDoc(any(), any());
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultResponseDoc(any(), any());
        org.mockito.Mockito.verifyNoInteractions(attachmentDocuments, attachmentEforms, attachmentHrms, attachmentForms, routing);
    }

    @Test
    void shouldValidateAllSelections_beforeCreatingUploadedDocument() {
        var request = requestSubmission();
        request.setAttachments(List.of(newDocumentAttachment(), new ConsultationAttachmentTo1(78, "E", true, "Foreign", null)));
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(documentManager);
    }

    @Test
    void shouldRejectUploadedDocument_whenReadPermissionIsMissing() {
        when(attachmentSecurity.hasPrivilege(loggedInInfo, "_edoc", "r", "123")).thenReturn(false);
        var request = requestSubmission();
        request.setAttachments(List.of(newDocumentAttachment()));
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request))
                .isInstanceOf(SecurityException.class);
        org.mockito.Mockito.verifyNoInteractions(documentManager);
    }

    @Test
    void shouldDeduplicateOwnedDocumentsIgnoringLabMetadata_forBothParents() {
        var link = new io.github.carlos_emr.carlos.commn.model.CtlDocument();
        link.setId(new io.github.carlos_emr.carlos.commn.model.CtlDocumentPK("demographic", 123, 77));
        when(attachmentDocuments.findCtlDocsAndDocsByDocNo(77))
                .thenReturn(java.util.Collections.singletonList(new Object[] {new Document(), link}));
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        var first = new ConsultationAttachmentTo1(77, "D", true, "Document", null);
        var duplicate = new ConsultationAttachmentTo1(77, "D", true, "Document", null);
        duplicate.setLabType("MDS");
        var request = requestSubmission();
        request.setAttachments(List.of(first, duplicate));
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of());
        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);
        var response = responseSubmission(null);
        response.setAttachments(List.of(first, duplicate));
        when(consultationManager.getConsultResponseDocs(loggedInInfo, 456)).thenReturn(List.of());
        ReflectionTestUtils.invokeMethod(service, "saveResponseAttachments", response);
        org.mockito.Mockito.verify(consultationManager).saveConsultRequestDoc(any(), any());
        org.mockito.Mockito.verify(consultationManager).saveConsultResponseDoc(any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldRollbackAndReportForbidden_whenRestSelectionRemovesRestrictedAttachment(boolean responsePath) {
        when(attachmentSecurity.hasPrivilege(loggedInInfo, "_edoc", "r", "123")).thenReturn(false);
        var transaction = responseTransaction();
        if (responsePath) {
            var response = responseSubmission(null);
            response.setAttachments(List.of());
            when(consultationManager.getResponse(loggedInInfo, 456)).thenReturn(storedResponse());
            when(consultationManager.getConsultResponseDocs(loggedInInfo, 456)).thenReturn(List.of(
                    new io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc(456, 77, "D", PROVIDER_NO)));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveResponse(response))
                    .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
        } else {
            var request = requestSubmission();
            request.setAttachments(List.of());
            var demographics = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.DemographicManager.class);
            ReflectionTestUtils.setField(service, "demographicManager", demographics);
            when(demographics.getDemographic(loggedInInfo, DEMOGRAPHIC_NO))
                    .thenReturn(new io.github.carlos_emr.carlos.commn.model.Demographic());
            when(consultationManager.getRequest(loggedInInfo, 456)).thenReturn(storedRequest());
            when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(List.of(
                    new io.github.carlos_emr.carlos.commn.model.ConsultDocs(456, 77, "D", PROVIDER_NO)));
            assertThat(service.updateConsultation(request).getStatus()).isEqualTo(403);
        }
        org.mockito.Mockito.verify(transaction).rollback(any());
        org.mockito.Mockito.verify(transaction, org.mockito.Mockito.never()).commit(any());
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultRequestDoc(any(), any());
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).saveConsultResponseDoc(any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"X", "doc"})
    void shouldRejectUnknownTypes_withoutAttachmentWrites(String type) {
        assertRejectedSelection(type, IllegalArgumentException.class);
    }

    @Test
    void shouldRejectRequestPatientMismatch_underParentLock() {
        var stored = storedRequest();
        stored.setDemographicId(999);
        when(requestDao.lockForAttachmentSync(456)).thenReturn(stored);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", requestSubmission()))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verify(consultationManager, org.mockito.Mockito.never()).getConsultRequestDocs(any(), any());
        org.mockito.Mockito.verifyNoInteractions(attachmentSecurity, documentManager);
    }

    private static DocumentTo1 validDocument() {
        DocumentTo1 document = new DocumentTo1();
        document.setFileName("safe.pdf");
        document.setFileContents(FILE_CONTENTS);
        document.setDemographicNo(DEMOGRAPHIC_NO);
        document.setProviderNo(PROVIDER_NO);
        document.setContentType("application/pdf");
        return document;
    }
}
