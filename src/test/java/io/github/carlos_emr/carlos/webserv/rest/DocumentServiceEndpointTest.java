/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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

import java.io.IOException;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;

/**
 * HTTP-level endpoint tests for {@link DocumentService} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-RS pipeline: path routing,
 * JSON serialization via Jackson, query parameter binding, and HTTP
 * status codes. Dependencies are mocked — no database required.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("DocumentService REST endpoint tests")
class DocumentServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private DocumentManager mockDocumentManager;

    @Mock
    private ProgramManager2 mockProgramManager2;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Override
    protected Object getServiceBean() {
        DocumentService service = new DocumentService();
        injectDependency(service, "documentManager", mockDocumentManager);
        injectDependency(service, "programManager2", mockProgramManager2);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        return service;
    }

    @BeforeEach
    void setUpSecurity() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any()))
            .thenReturn(true);
    }

    private DocumentTo1 createValidDocumentTo1() {
        DocumentTo1 doc = new DocumentTo1();
        doc.setFileName("test-report.pdf");
        doc.setFileContents(new byte[]{1, 2, 3, 4});
        doc.setDemographicNo(123);
        doc.setProviderNo("999");
        doc.setContentType("application/pdf");
        return doc;
    }

    /** Tests for POST /document/saveDocumentToDemographic endpoint. */
    @Nested
    @DisplayName("POST /document/saveDocumentToDemographic")
    class SaveDocumentToDemographic {

        @Test
        @DisplayName("should return 200 when document is saved successfully")
        void shouldReturn200_whenDocumentSavedSuccessfully() throws IOException {
            DocumentTo1 docTo1 = createValidDocumentTo1();
            Document savedDoc = new Document();
            savedDoc.setDocumentNo(1);

            when(mockDocumentManager.createDocument(any(LoggedInInfo.class), any(Document.class), eq(123), eq("999"), any(byte[].class)))
                .thenReturn(savedDoc);

            Response response = request().path("/document/saveDocumentToDemographic")
                .post(Entity.json(docTo1));

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 400 when required fields are missing")
        void shouldReturn400_whenRequiredFieldsMissing() {
            DocumentTo1 emptyDoc = new DocumentTo1();

            Response response = request().path("/document/saveDocumentToDemographic")
                .post(Entity.json(emptyDoc));

            assertThat(response.getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("should return 500 when document save throws IOException")
        void shouldReturn500_whenDocumentSaveThrowsIOException() throws IOException {
            DocumentTo1 docTo1 = createValidDocumentTo1();

            when(mockDocumentManager.createDocument(any(LoggedInInfo.class), any(Document.class), eq(123), eq("999"), any(byte[].class)))
                .thenThrow(new IOException("Disk full"));

            Response response = request().path("/document/saveDocumentToDemographic")
                .post(Entity.json(docTo1));

            assertThat(response.getStatus()).isEqualTo(500);
        }
    }

    /** Tests for POST /document/uploadPendingDocuments endpoint. */
    @Nested
    @DisplayName("POST /document/uploadPendingDocuments")
    class UploadPendingDocuments {

        @Test
        @DisplayName("should return 400 when required fields are missing")
        void shouldReturn400_whenRequiredFieldsMissing() {
            DocumentTo1 emptyDoc = new DocumentTo1();
            emptyDoc.setFileName("test.pdf");

            Response response = request().path("/document/uploadPendingDocuments")
                .post(Entity.json(emptyDoc));

            assertThat(response.getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("should return 403 when user lacks edoc write privilege")
        void shouldReturn403_whenAccessDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq(SecurityInfoManager.WRITE), eq("")))
                .thenReturn(false);

            DocumentTo1 docTo1 = createValidDocumentTo1();
            docTo1.setQueue(1);
            docTo1.setContentType("application/pdf");

            Response response = request().path("/document/uploadPendingDocuments")
                .post(Entity.json(docTo1));

            assertThat(response.getStatus()).isEqualTo(403);
        }
    }
}
