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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regression tests for document REST service validation and error handling.
 *
 * @since 2026-05-26
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService regression tests")
@Tag("unit")
@Tag("rest")
@Tag("regression")
class DocumentServiceRegressionTest {

    private static final Integer DEMOGRAPHIC_NO = 123;
    private static final String PROVIDER_NO = "999998";
    private static final byte[] FILE_CONTENTS = "document body".getBytes(StandardCharsets.UTF_8);

    @Mock
    private DocumentManager documentManager;

    @Mock
    private ProgramManager2 programManager2;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        ReflectionTestUtils.setField(service, "documentManager", documentManager);
        ReflectionTestUtils.setField(service, "programManager2", programManager2);
        ReflectionTestUtils.setField(service, "securityInfoManager", securityInfoManager);
    }

    @Test
    @DisplayName("should return bad request when save payload has null file contents")
    void shouldReturnBadRequest_whenSavePayloadHasNullFileContents() {
        DocumentTo1 document = validDocument();
        document.setFileContents(null);

        Response response = service.saveDocumentToDemographic(document);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        verifyNoInteractions(documentManager);
    }

    @Test
    @DisplayName("should return bad request when save payload has empty file contents")
    void shouldReturnBadRequest_whenSavePayloadHasEmptyFileContents() {
        DocumentTo1 document = validDocument();
        document.setFileContents(new byte[0]);

        Response response = service.saveDocumentToDemographic(document);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        verifyNoInteractions(documentManager);
    }

    @Test
    @DisplayName("should return bad request when save document filename validation is wrapped")
    void shouldReturnBadRequest_whenSaveDocumentFilenameValidationIsWrapped() throws Exception {
        String validationMessage = "unsafe filename ../secret.pdf";
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(DEMOGRAPHIC_NO),
                eq(PROVIDER_NO), eq(FILE_CONTENTS)))
                .thenThrow(new IOException("Document filename failed path validation",
                        new FileValidationException(validationMessage)));

        Response response = service.saveDocumentToDemographic(validDocument());

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("Invalid filename.");
        assertThat(response.getEntity().toString()).doesNotContain("../secret.pdf");
    }

    @Test
    @DisplayName("should return bad request when pending document filename validation is wrapped")
    void shouldReturnBadRequest_whenPendingDocumentFilenameValidationIsWrapped() throws Exception {
        String validationMessage = "unsafe filename ../secret.pdf";
        DocumentTo1 document = validPendingDocument();
        grantPendingDocumentWriteAccess();
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(DEMOGRAPHIC_NO),
                eq(PROVIDER_NO), eq(FILE_CONTENTS)))
                .thenThrow(new IOException("Document filename failed path validation",
                        new FileValidationException(validationMessage)));

        Response response = service.uploadPendingDocuments(document, null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("Invalid filename.");
        assertThat(response.getEntity().toString()).doesNotContain("../secret.pdf");
    }

    @Test
    @DisplayName("should hide internal exception message when pending document save fails")
    void shouldHideInternalExceptionMessage_whenPendingDocumentSaveFails() throws Exception {
        DocumentTo1 document = validPendingDocument();
        grantPendingDocumentWriteAccess();
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(DEMOGRAPHIC_NO),
                eq(PROVIDER_NO), eq(FILE_CONTENTS)))
                .thenThrow(new IOException("disk path /tmp/upload failed"));

        Response response = service.uploadPendingDocuments(document, null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        Map<String, String> entity = responseEntity(response);
        assertThat(entity.get("message")).isEqualTo("The document could not be saved.");
        assertThat(entity).doesNotContainKey("fileName");
    }

    private void grantPendingDocumentWriteAccess() {
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, ""))
                .thenReturn(true);
        when(programManager2.getCurrentProgramInDomain(loggedInInfo, PROVIDER_NO)).thenReturn(null);
    }

    private static DocumentTo1 validPendingDocument() {
        DocumentTo1 document = validDocument();
        document.setQueue(1);
        document.setContentType("application/pdf");
        return document;
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

    @SuppressWarnings("unchecked")
    private static Map<String, String> responseEntity(Response response) {
        return (Map<String, String>) response.getEntity();
    }
}
