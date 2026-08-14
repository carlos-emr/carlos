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

import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationAttachmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationResponseTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
class ConsultationWebServiceRegressionTest {

    private static final Integer DEMOGRAPHIC_NO = 123;
    private static final String PROVIDER_NO = "999998";
    private static final byte[] FILE_CONTENTS = "document body".getBytes(StandardCharsets.UTF_8);

    @Mock
    private DocumentManager documentManager;

    @Mock
    private ConsultationManager consultationManager;

    @Mock
    private OutboundEmailArchiveDao outboundEmailArchiveDao;

    @Mock
    private LoggedInInfo loggedInInfo;

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
        ReflectionTestUtils.setField(service, "outboundEmailArchiveDao", outboundEmailArchiveDao);
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

    @Test
    @DisplayName("should reject outbound archive documents when saving request attachments")
    void shouldRejectOutboundArchiveDocumentsWhenSavingRequestAttachments() {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        request.setAttachments(List.of(existingDocumentAttachment(321)));
        when(consultationManager.getConsultRequestDocs(loggedInInfo, request.getId())).thenReturn(new ArrayList<>());
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(consultationManager, never()).saveConsultRequestDoc(eq(loggedInInfo), any(ConsultDocs.class));
    }

    @Test
    @DisplayName("should reject outbound archive documents when saving response attachments")
    void shouldRejectOutboundArchiveDocumentsWhenSavingResponseAttachments() {
        ConsultationResponseTo1 response = new ConsultationResponseTo1();
        response.setId(789);
        response.setAttachments(List.of(existingDocumentAttachment(321)));
        when(consultationManager.getConsultResponseDocs(loggedInInfo, response.getId())).thenReturn(new ArrayList<>());
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "saveResponseAttachments", response))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(consultationManager, never()).saveConsultResponseDoc(eq(loggedInInfo), any(ConsultResponseDoc.class));
    }

    @Test
    @DisplayName("should reject outbound archive response attachments even when validationError is supplied")
    void shouldRejectOutboundArchiveResponseAttachmentsEvenWithValidationError() {
        ConsultationAttachmentTo1 attachment = existingDocumentAttachment(321);
        attachment.setValidationError("client supplied validation error");
        ConsultationResponseTo1 response = new ConsultationResponseTo1();
        response.setId(789);
        response.setAttachments(List.of(attachment));
        when(consultationManager.getConsultResponseDocs(loggedInInfo, response.getId())).thenReturn(new ArrayList<>());
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "saveResponseAttachments", response))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(consultationManager, never()).saveConsultResponseDoc(eq(loggedInInfo), any(ConsultResponseDoc.class));
    }

    @Test
    @DisplayName("should preserve hidden outbound archive attachments during request saves")
    void shouldPreserveHiddenOutboundArchiveAttachmentsDuringRequestSave() {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(DEMOGRAPHIC_NO);
        request.setAttachments(new ArrayList<>());
        ConsultDocs archiveAttachment = new ConsultDocs(
                request.getId(), 321, ConsultationAttachmentTo1.TYPE_DOC, PROVIDER_NO);
        List<ConsultDocs> currentDocs = new ArrayList<>(List.of(archiveAttachment));
        when(consultationManager.getConsultRequestDocs(loggedInInfo, request.getId()))
                .thenReturn(currentDocs);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        ReflectionTestUtils.invokeMethod(service, "saveRequestAttachments", request);

        assertThat(archiveAttachment.getDeleted()).isNull();
    }

    private static ConsultationAttachmentTo1 newDocumentAttachment() {
        ConsultationAttachmentTo1 attachment = new ConsultationAttachmentTo1();
        attachment.setDocumentType(ConsultationAttachmentTo1.TYPE_DOC);
        attachment.setAttached(true);
        attachment.setDocument(validDocument());
        return attachment;
    }

    private static ConsultationAttachmentTo1 existingDocumentAttachment(int documentNo) {
        ConsultationAttachmentTo1 attachment = new ConsultationAttachmentTo1();
        attachment.setDocumentNo(documentNo);
        attachment.setDocumentType(ConsultationAttachmentTo1.TYPE_DOC);
        attachment.setAttached(true);
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
