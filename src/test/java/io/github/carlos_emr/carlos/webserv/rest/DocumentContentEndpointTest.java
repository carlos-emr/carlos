/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.webserv.rest.exceptionMapping.GeneralExceptionMapper;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

@Tag("unit")
class DocumentContentEndpointTest extends CarlosRestTestBase {
    @Mock private DocumentManager documents;
    @Mock private SecurityInfoManager security;
    @TempDir Path directory;
    private String originalDirectory;
    @Override protected Object getServiceBean() {
        var service = new DocumentService();
        injectDependency(service,"documentManager",documents);
        injectDependency(service,"securityInfoManager",security); return service;
    }
    @Override protected List<Object> additionalServerProviders() { return List.of(new GeneralExceptionMapper()); }
    @BeforeEach void setupContent() {
        originalDirectory = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR",directory.toString());
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r","")).thenReturn(true);
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r",42)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(true);
    }
    @AfterEach void restoreDirectory() {
        if (originalDirectory == null) CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        else CarlosProperties.getInstance().setProperty("DOCUMENT_DIR",originalDirectory);
    }
    private Document file(String filename, byte[] bytes) throws Exception {
        if (bytes != null) Files.write(directory.resolve(filename),bytes);
        Document row = spy(new Document(7)); row.setDocfilename(filename);
        when(documents.getDocument(mockLoggedInInfo,7)).thenReturn(row);
        var link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic",42,7));
        when(documents.getCtlDocumentByDocumentId(mockLoggedInInfo,7)).thenReturn(link);
        return row;
    }
    private Response content() { return request().path("/document/7/content").replaceHeader("Accept", MediaType.APPLICATION_OCTET_STREAM).get(); }
    @Test void shouldStreamExactBytes_withSafeDownloadHeaders_withoutMaterializingTheDomainByteArray() throws Exception {
        byte[] expected = new byte[1_048_593]; new java.util.Random(42).nextBytes(expected);
        Document row = file("PRIVATE_patient.pdf",expected);
        try (Response response = content()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(byte[].class)).isEqualTo(expected);
            assertThat(response.getHeaderString("Content-Disposition")).isEqualTo("attachment");
            assertThat(response.getHeaderString("Cache-Control")).isEqualTo("no-store");
            assertThat(response.getHeaderString("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_TYPE);
        }
        verify(row,never()).getDocumentFileContentsAsBytes();
    }
    @Test void shouldDenyGlobalRead_beforeAnyDocumentLookup() {
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r","")).thenReturn(false);
        assertThat(content().getStatus()).isEqualTo(403); verifyNoInteractions(documents);
    }
    @Test void shouldDenyPatientRead_beforeAnyFileAccess() throws Exception {
        Document row = file("PRIVATE_patient.pdf",new byte[]{1});
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r",42)).thenReturn(false);
        assertThat(content().getStatus()).isEqualTo(403); verify(row,never()).getDocumentFileFullPath();
    }
    @Test void shouldDenyBlockedChart_evenWhenDocumentPrivilegeIsGranted() throws Exception {
        Document row = file("PRIVATE_patient.pdf",new byte[]{1});
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(false);
        assertThat(content().getStatus()).isEqualTo(403); verify(row,never()).getDocumentFileFullPath();
    }
    @Test void shouldReturn404_forAnAbsentRecord() {
        assertThat(content().getStatus()).isEqualTo(404);
        verify(documents,never()).getCtlDocumentByDocumentId(any(),any());
    }
    @Test void shouldFailExplicitly_forMissingContent_withoutExposingItsPath() throws Exception {
        file("PRIVATE_patient.pdf",null);
        try (Response response = content()) {
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.readEntity(String.class)).contains("INTERNAL_ERROR").doesNotContain("PRIVATE",directory.toString());
        }
    }
    @Test void shouldRefuseTraversalOutsideTheConfiguredDocumentDirectory() throws Exception {
        Document row = file("placeholder",new byte[]{1}); row.setDocfilename("../PRIVATE_patient.pdf");
        try (Response response = content()) {
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.readEntity(String.class)).doesNotContain("PRIVATE",directory.toString());
        }
    }
    @Test void shouldRejectInvalidIds_withoutLookingUpARecord() {
        assertThat(request().path("/document/0/content").replaceHeader("Accept", MediaType.APPLICATION_OCTET_STREAM).get().getStatus()).isEqualTo(400);
        verifyNoInteractions(documents);
    }
    @Test void shouldPropagateAnOutputFailure_andCloseTheFileStream() throws Exception {
        file("content.bin", new byte[]{1,2,3});
        var service = new DocumentService() {
            @Override protected io.github.carlos_emr.carlos.utility.LoggedInInfo getLoggedInInfo() {
                return mockLoggedInInfo;
            }
        };
        injectDependency(service,"documentManager",documents);
        injectDependency(service,"securityInfoManager",security);
        try (Response response = service.getContent(7)) {
            var content = (jakarta.ws.rs.core.StreamingOutput) response.getEntity();
            var expected = new java.io.IOException("controlled destination failure");
            var output = new java.io.OutputStream() {
                @Override public void write(int value) throws java.io.IOException { throw expected; }
            };
            assertThatThrownBy(() -> content.write(output)).isSameAs(expected);
        }
        Files.delete(directory.resolve("content.bin"));
    }

}
