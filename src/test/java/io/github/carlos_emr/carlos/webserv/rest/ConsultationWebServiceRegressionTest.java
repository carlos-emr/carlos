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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ConsultationResponse;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationAttachmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationResponseTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ConsultationWebServiceRegressionTest {

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
    private AttachmentOwnershipService attachmentOwnershipService;

    @Mock
    private DemographicManager demographicManager;

    private ConsultationWebService service;

    @BeforeEach
    void setUp() {
        service = new ConsultationWebService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        ReflectionTestUtils.setField(service, "documentManager", documentManager);
        ReflectionTestUtils.setField(service, "consultationManager", consultationManager);
        ReflectionTestUtils.setField(service, "attachmentOwnershipService", attachmentOwnershipService);
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);
    }

    /**
     * Issue #3867: an update that changed demographicId moved the consultation to another patient
     * while its already-verified attachments stayed linked without being re-checked.
     */
    @Test
    @DisplayName("should refuse to move an existing consultation to another patient")
    void shouldRejectUpdate_whenDemographicIdChanges() {
        ConsultationRequest existing = new ConsultationRequest();
        existing.setDemographicId(555);
        when(consultationManager.getRequest(loggedInInfo, 456)).thenReturn(existing);
        when(demographicManager.getDemographic(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(new Demographic());
        ConsultationRequestTo1 data = new ConsultationRequestTo1();
        data.setId(456);
        data.setDemographicId(DEMOGRAPHIC_NO);
        data.setReferralDate(new Date());
        data.setServiceId(1);
        data.setUrgency("1");
        data.setStatus("1");

        Response response = service.updateConsultation(data);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(existing.getDemographicId()).isEqualTo(555);
        verify(consultationManager, never()).saveConsultationRequest(any(), any());
        verify(consultationManager, never()).saveConsultRequestDoc(any(), any());
    }

    /**
     * Issue #3867: saving an existing consultation response with another patient moved the response
     * while its already-verified attachments stayed linked, as updateConsultation did before.
     */
    @Test
    @DisplayName("should refuse to move an existing consultation response to another patient")
    void shouldRejectResponseSave_whenDemographicChanges() {
        ConsultationResponse existing = new ConsultationResponse();
        existing.setDemographicNo(555);
        when(consultationManager.getResponse(loggedInInfo, 789)).thenReturn(existing);
        ConsultationResponseTo1 data = new ConsultationResponseTo1();
        data.setId(789);
        DemographicTo1 demographic = new DemographicTo1();
        demographic.setDemographicNo(DEMOGRAPHIC_NO);
        data.setDemographic(demographic);
        data.setAttachments(new ArrayList<>(List.of(existingAttachment(ConsultationAttachmentTo1.TYPE_DOC, 10))));

        assertThatThrownBy(() -> service.saveResponse(data))
                .isInstanceOfSatisfying(WebApplicationException.class,
                        e -> assertThat(e.getResponse().getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode()));

        assertThat(existing.getDemographicNo()).isEqualTo(555);
        verify(consultationManager, never()).saveConsultationResponse(any(), any());
        verify(consultationManager, never()).getConsultResponseDocs(any(), any());
        verify(consultationManager, never()).saveConsultResponseDoc(any(), any());
    }

    @Test
    @DisplayName("should answer 404 without saving when the consultation response does not exist")
    void shouldReturnNotFound_whenResponseUnknown() {
        ConsultationResponseTo1 data = new ConsultationResponseTo1();
        data.setId(790);

        assertThatThrownBy(() -> service.saveResponse(data))
                .isInstanceOfSatisfying(WebApplicationException.class,
                        e -> assertThat(e.getResponse().getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode()));

        verify(consultationManager, never()).saveConsultationResponse(any(), any());
        verify(consultationManager, never()).saveConsultResponseDoc(any(), any());
    }

    /**
     * Issue #3867: the REST consultation save attached any existing record id it was given, and the
     * consultation print, fax and Ocean renderers resolve attachments by id alone.
     */
    @Test
    @DisplayName("should refuse to attach an existing record that belongs to another patient")
    void shouldRefuseForeignAttachment_whenSavingRequestAttachments() {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        ConsultationAttachmentTo1 owned = existingAttachment(ConsultationAttachmentTo1.TYPE_DOC, 10);
        ConsultationAttachmentTo1 foreign = existingAttachment(ConsultationAttachmentTo1.TYPE_LAB, 999);
        request.setAttachments(new ArrayList<>(List.of(owned, foreign)));
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(new ArrayList<>());
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(attachmentOwnershipService.findAttachableIds(DocumentType.DOC, DEMOGRAPHIC_NO, List.of(10))).thenReturn(Set.of(10));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.LAB, DEMOGRAPHIC_NO, List.of(999))).thenReturn(Set.of());

        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);

        ArgumentCaptor<ConsultDocs> saved = ArgumentCaptor.forClass(ConsultDocs.class);
        verify(consultationManager).saveConsultRequestDoc(eq(loggedInInfo), saved.capture());
        assertThat(saved.getAllValues()).extracting(ConsultDocs::getDocumentNo).containsExactly(10);
        assertThat(foreign.getValidationError()).isEqualTo("Attachment could not be verified for this patient");
        assertThat(owned.getValidationError()).isNull();
    }

    /**
     * Review follow-up for issue #3867: refusing a foreign new attachment must not detach the
     * consultation's existing attachments. Only rows the caller left out of the list are detached.
     */
    @Test
    @DisplayName("should keep resubmitted attachments and detach only omitted ones when a new foreign attachment is refused")
    void shouldKeepResubmittedAttachments_whenForeignNewAttachmentRefused() {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        ConsultationAttachmentTo1 kept = existingAttachment(ConsultationAttachmentTo1.TYPE_DOC, 10);
        ConsultationAttachmentTo1 foreign = existingAttachment(ConsultationAttachmentTo1.TYPE_LAB, 999);
        request.setAttachments(new ArrayList<>(List.of(kept, foreign)));
        ConsultDocs keptRow = new ConsultDocs(456, 10, ConsultationAttachmentTo1.TYPE_DOC, PROVIDER_NO);
        ReflectionTestUtils.setField(keptRow, "id", 1);
        ConsultDocs omittedRow = new ConsultDocs(456, 20, ConsultationAttachmentTo1.TYPE_EFORM, PROVIDER_NO);
        ReflectionTestUtils.setField(omittedRow, "id", 2);
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(new ArrayList<>(List.of(keptRow, omittedRow)));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.DOC, DEMOGRAPHIC_NO, List.of(10))).thenReturn(Set.of(10));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.LAB, DEMOGRAPHIC_NO, List.of(999))).thenReturn(Set.of());

        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);

        ArgumentCaptor<ConsultDocs> saved = ArgumentCaptor.forClass(ConsultDocs.class);
        verify(consultationManager).saveConsultRequestDoc(eq(loggedInInfo), saved.capture());
        assertThat(saved.getAllValues()).containsExactly(omittedRow);
        assertThat(omittedRow.getDeleted()).isEqualTo(ConsultDocs.DELETED);
        assertThat(foreign.getValidationError()).isEqualTo("Attachment could not be verified for this patient");
        assertThat(kept.getValidationError()).isNull();
    }

    /**
     * Issue #3867, same policy as the consultation form: an already-attached row that no longer
     * verifies (a legacy foreign row) is detached on save instead of being kept because the caller
     * resubmitted its id.
     */
    @Test
    @DisplayName("should detach a resubmitted existing request attachment that is not the patient's")
    void shouldDetachExistingRequestAttachment_whenNotOwnedByPatient() {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        ConsultationAttachmentTo1 legacyForeign = existingAttachment(ConsultationAttachmentTo1.TYPE_LAB, 999);
        request.setAttachments(new ArrayList<>(List.of(legacyForeign)));
        ConsultDocs legacyRow = new ConsultDocs(456, 999, ConsultationAttachmentTo1.TYPE_LAB, PROVIDER_NO);
        ReflectionTestUtils.setField(legacyRow, "id", 3);
        when(consultationManager.getConsultRequestDocs(loggedInInfo, 456)).thenReturn(new ArrayList<>(List.of(legacyRow)));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.LAB, DEMOGRAPHIC_NO, List.of(999))).thenReturn(Set.of());

        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);

        verify(consultationManager).saveConsultRequestDoc(loggedInInfo, legacyRow);
        assertThat(legacyRow.getDeleted()).isEqualTo(ConsultDocs.DELETED);
        assertThat(legacyForeign.getValidationError()).isEqualTo("Attachment could not be verified for this patient");
    }

    @Test
    @DisplayName("should detach a resubmitted existing response attachment that is not the patient's and keep an owned one")
    void shouldDetachExistingResponseAttachment_whenNotOwnedByPatient() {
        ConsultationResponseTo1 response = new ConsultationResponseTo1();
        response.setId(789);
        ConsultationAttachmentTo1 owned = existingAttachment(ConsultationAttachmentTo1.TYPE_DOC, 10);
        ConsultationAttachmentTo1 legacyForeign = existingAttachment(ConsultationAttachmentTo1.TYPE_EFORM, 999);
        response.setAttachments(new ArrayList<>(List.of(owned, legacyForeign)));
        ConsultResponseDoc ownedRow = new ConsultResponseDoc(789, 10, ConsultationAttachmentTo1.TYPE_DOC, PROVIDER_NO);
        ReflectionTestUtils.setField(ownedRow, "id", 4);
        ConsultResponseDoc legacyRow = new ConsultResponseDoc(789, 999, ConsultationAttachmentTo1.TYPE_EFORM, PROVIDER_NO);
        ReflectionTestUtils.setField(legacyRow, "id", 3);
        when(consultationManager.getConsultResponseDocs(loggedInInfo, 789)).thenReturn(new ArrayList<>(List.of(ownedRow, legacyRow)));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.DOC, DEMOGRAPHIC_NO, List.of(10))).thenReturn(Set.of(10));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.EFORM, DEMOGRAPHIC_NO, List.of(999))).thenReturn(Set.of());

        ReflectionTestUtils.invokeMethod(service, "saveResponseAttachments", response, DEMOGRAPHIC_NO);

        ArgumentCaptor<ConsultResponseDoc> saved = ArgumentCaptor.forClass(ConsultResponseDoc.class);
        verify(consultationManager).saveConsultResponseDoc(eq(loggedInInfo), saved.capture());
        assertThat(saved.getAllValues()).containsExactly(legacyRow);
        assertThat(legacyRow.getDeleted()).isEqualTo(ConsultResponseDoc.DELETED);
        assertThat(legacyForeign.getValidationError()).isEqualTo("Attachment could not be verified for this patient");
        assertThat(owned.getValidationError()).isNull();
    }

    @Test
    @DisplayName("should refuse an attachment with an unknown type code")
    void shouldRefuseAttachment_whenTypeCodeUnknown() {
        assertThat(service.isAttachmentOwnedBy(DEMOGRAPHIC_NO, existingAttachment("Z", 1))).isFalse();
        verify(attachmentOwnershipService, never()).findAttachableIds(any(DocumentType.class), any(), any());
    }

    private static ConsultationAttachmentTo1 existingAttachment(String type, int documentNo) {
        ConsultationAttachmentTo1 attachment = new ConsultationAttachmentTo1();
        attachment.setDocumentType(type);
        attachment.setDocumentNo(documentNo);
        attachment.setAttached(true);
        return attachment;
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
        when(consultationManager.getConsultRequestDocs(loggedInInfo, request.getId())).thenReturn(null);

        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);

        assertThat(request.getAttachments()).hasSize(1);
        assertThat(request.getAttachments().get(0).getValidationError()).isEqualTo("Invalid attachment filename");
        assertThat(request.getAttachments().get(0).getDocumentNo()).isZero();
    }

    private static ConsultationAttachmentTo1 newDocumentAttachment() {
        ConsultationAttachmentTo1 attachment = new ConsultationAttachmentTo1();
        attachment.setDocumentType(ConsultationAttachmentTo1.TYPE_DOC);
        attachment.setAttached(true);
        attachment.setDocument(validDocument());
        return attachment;
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
