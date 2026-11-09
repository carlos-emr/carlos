/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.webserv.rest.exceptionMapping.GeneralExceptionMapper;
import io.github.carlos_emr.carlos.webserv.rest.exceptionMapping.SecurityExceptionMapper;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

@Tag("unit")
class DocumentContentEndpointTest extends CarlosRestTestBase {
    @Mock private DocumentManager documents;
    @Mock private SecurityInfoManager security;
    @Mock private CtlDocumentDao links;
    @Mock private DocumentDao storedDocuments;
    @Mock private PatientLabRoutingDao routes;
    @Mock private QueueDocumentLinkDao queues;
    @Mock private ProgramManager2 programs;
    @TempDir Path directory;
    private String originalDirectory;
    @Override protected Object getServiceBean() {
        var service = new DocumentService();
        injectDependency(service,"documentManager",documents);
        injectDependency(service,"securityInfoManager",security); return service;
    }
    @Override protected List<Object> additionalServerProviders() {
        return List.of(new GeneralExceptionMapper(), new SecurityExceptionMapper());
    }
    @BeforeEach void setupContent() {
        originalDirectory = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR",directory.toString());
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r","")).thenReturn(true);
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r",42)).thenReturn(true);
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r",(String)null)).thenReturn(true);
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r","42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(true);
        registerMock(CtlDocumentDao.class, links);
        registerMock(DocumentDao.class, storedDocuments);
        registerMock(PatientLabRoutingDao.class, routes);
        registerMock(QueueDocumentLinkDao.class, queues);
        registerMock(ProgramManager2.class, programs);
    }
    @AfterEach void restoreDirectory() {
        if (originalDirectory == null) CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        else CarlosProperties.getInstance().setProperty("DOCUMENT_DIR",originalDirectory);
    }
    private Document file(String filename, byte[] bytes) throws Exception {
        if (bytes != null) Files.write(directory.resolve(filename),bytes);
        Document row = spy(new Document(7)); row.setDocfilename(filename);
        when(documents.getDocument(mockLoggedInInfo,7)).thenReturn(row);
        when(storedDocuments.find(7)).thenReturn(row);
        var link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic",42,7));
        when(documents.getCtlDocumentByDocumentId(mockLoggedInInfo,7)).thenReturn(link);
        when(links.findByDocumentNoAndModule(7,"demographic")).thenReturn(List.of(link));
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
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r","42")).thenReturn(false);
        assertThat(content().getStatus()).isEqualTo(403); verify(row,never()).getDocumentFileFullPath();
    }
    @Test void shouldDenyBlockedChart_evenWhenDocumentPrivilegeIsGranted() throws Exception {
        Document row = file("PRIVATE_patient.pdf",new byte[]{1});
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(false);
        assertThat(content().getStatus()).isEqualTo(403); verify(row,never()).getDocumentFileFullPath();
    }
    @Test void shouldDenyASecondLinkedPatient_beforeResolvingTheFile() throws Exception {
        Document row = file("private.pdf",new byte[]{1});
        var denied = new CtlDocument(); denied.setId(new CtlDocumentPK("demographic",43,7));
        CtlDocument first = documents.getCtlDocumentByDocumentId(mockLoggedInInfo,7);
        when(links.findByDocumentNoAndModule(7,"demographic")).thenReturn(List.of(first,denied));
        assertDeniedWithoutFileAccess(row);
    }
    @Test void shouldDenyADocRoutedPatient_beforeResolvingTheFile() throws Exception {
        Document row = file("private.pdf",new byte[]{1});
        when(documents.getCtlDocumentByDocumentId(mockLoggedInInfo,7)).thenReturn(null);
        when(links.findByDocumentNoAndModule(7,"demographic")).thenReturn(List.of());
        route(43);
        assertDeniedWithoutFileAccess(row);
    }
    @Test void shouldDenyThePersistedProgramRestriction_beforeResolvingTheFile() throws Exception {
        Document row = file("private.pdf",new byte[]{1});
        row.setRestrictToProgram(true); row.setProgramId(17);
        assertDeniedWithoutFileAccess(row);
    }
    @Test void shouldDenyAnUnreadableActiveQueue_beforeResolvingTheFile() throws Exception {
        Document row = file("private.pdf",new byte[]{1});
        var queue = new QueueDocumentLink(); queue.setQueueId(9); queue.setStatus("A");
        when(queues.getQueueFromDocument(7)).thenReturn(List.of(queue));
        assertDeniedWithoutFileAccess(row);
    }
    @ParameterizedTest
    @ValueSource(ints={0,-1})
    void shouldReturnUnassignedDocumentBytes_withSupportedInboxSentinels(int sentinel) throws Exception {
        byte[] bytes = {1,2,3}; file("unassigned.pdf",bytes);
        var link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic",sentinel,7));
        when(documents.getCtlDocumentByDocumentId(mockLoggedInInfo,7)).thenReturn(link);
        when(links.findByDocumentNoAndModule(7,"demographic")).thenReturn(List.of(link));
        try (Response response = content()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(byte[].class)).isEqualTo(bytes);
        }
        verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());
    }
    @Test void shouldRequirePatientDocumentRead_forAnOtherwisePermittedDocRoute() throws Exception {
        Document row = file("private.pdf",new byte[]{1});
        route(43);
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,43)).thenReturn(true);
        assertDeniedWithoutFileAccess(row);
    }
    @Test void shouldReturnBytes_withEveryLinkedAndRoutedPatientAuthorized() throws Exception {
        byte[] bytes = {4,5,6}; file("shared.pdf",bytes); route(43);
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,43)).thenReturn(true);
        when(security.hasPrivilege(mockLoggedInInfo,"_edoc","r","43")).thenReturn(true);
        try (Response response = content()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(byte[].class)).isEqualTo(bytes);
        }
        verify(security).hasPrivilege(mockLoggedInInfo,"_edoc","r","42");
        verify(security).hasPrivilege(mockLoggedInInfo,"_edoc","r","43");
    }
    @Test void shouldReturnBytes_withStoredProgramAndActiveQueueAccess() throws Exception {
        byte[] bytes = {7,8,9}; Document row = file("restricted.pdf",bytes);
        row.setRestrictToProgram(true); row.setProgramId(17);
        var membership = new io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider();
        membership.setProgramId(17L);
        when(programs.getProgramDomain(mockLoggedInInfo,mockLoggedInInfo.getLoggedInProviderNo()))
                .thenReturn(List.of(membership));
        var queue = new QueueDocumentLink(); queue.setQueueId(9); queue.setStatus("A");
        when(queues.getQueueFromDocument(7)).thenReturn(List.of(queue));
        when(security.hasPrivilege(mockLoggedInInfo,"_queue.9","r",(String)null)).thenReturn(true);
        try (Response response = content()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(byte[].class)).isEqualTo(bytes);
        }
    }
    @ParameterizedTest
    @ValueSource(ints={0,-1})
    void shouldRetainProgramRestrictions_withUnassignedInboxSentinels(int sentinel) throws Exception {
        Document row = file("restricted.pdf",new byte[]{1});
        row.setRestrictToProgram(true); row.setProgramId(17);
        var link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic",sentinel,7));
        when(links.findByDocumentNoAndModule(7,"demographic")).thenReturn(List.of(link));
        assertDeniedWithoutFileAccess(row);
    }
    private void route(int patient) {
        var route = new PatientLabRouting(); route.setDemographicNo(patient);
        when(routes.findByLabNoAndLabType(7,"DOC")).thenReturn(List.of(route));
        when(routes.findDocByDemographic(7)).thenReturn(List.of(route));
    }
    private void assertDeniedWithoutFileAccess(Document row) throws java.io.IOException {
        try (Response response = content()) { assertThat(response.getStatus()).isEqualTo(403); }
        verify(row,never()).getDocumentFileFullPath();
        verify(row,never()).getDocumentFileContentsAsBytes();
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
            assertThat(response.getStatus()).isEqualTo(403);
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
