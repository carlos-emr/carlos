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

    private ConsultationWebService service;

    @BeforeEach
    void setUp() {
        service = new ConsultationWebService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
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
        when(consultationManager.getConsultRequestDocs(loggedInInfo, request.getId())).thenReturn(null);

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
