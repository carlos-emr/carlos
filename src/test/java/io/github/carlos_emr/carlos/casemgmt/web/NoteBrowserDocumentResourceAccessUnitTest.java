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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.Queue;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.apache.struts2.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Native document mutations must authorize their stored resource before SQL or file changes.
 * The real EDocUtil status updates and refile copy execute for allowed controls.
 *
 * @since 2026-10-25
 */
@Tag("unit")
@Tag("security")
class NoteBrowserDocumentResourceAccessUnitTest extends CarlosUnitTestBase {
    enum Operation { DELETE, UNDELETE, REFILE }
    @TempDir Path root;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private DocumentDao documents;
    private CtlDocumentDao links;
    private PatientLabRoutingDao routing;
    private QueueDocumentLinkDao queues;
    private ProgramManager2 programs;
    private Document document;
    private final List<CtlDocument> filings = new ArrayList<>();
    private final List<PatientLabRouting> routes = new ArrayList<>();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ActionContext previousContext;
    private Field cachedDocumentDao;
    private Object previousDocumentDao;
    private String previousDocumentDir;
    private String previousIncomingDir;
    private Path source;
    private Path destination;
    private final byte[] originalBytes = "%PDF-FAKE owned clinical file café 中文".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void prepareOwnedStoredDocument() throws Exception {
        security = createAndRegisterMock(SecurityInfoManager.class);
        login = org.mockito.Mockito.mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("4249");
        for (String object : List.of("_eChart", "_edoc")) {
            when(security.hasPrivilege(eq(login), eq(object), eq("w"), nullable(String.class))).thenReturn(true);
        }
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
        when(security.hasPrivilege(login, "_queue.7", "r", (String) null)).thenReturn(true);
        documents = createAndRegisterMock(DocumentDao.class);
        links = createAndRegisterMock(CtlDocumentDao.class);
        routing = createAndRegisterMock(PatientLabRoutingDao.class);
        queues = createAndRegisterMock(QueueDocumentLinkDao.class);
        programs = createAndRegisterMock(ProgramManager2.class);
        QueueDao queueDao = createAndRegisterMock(QueueDao.class);
        when(queueDao.find(7)).thenReturn(new Queue());
        when(queueDao.find(1)).thenReturn(new Queue());
        document = new Document();
        document.setDocumentNo(42);
        document.setDocfilename("20261025000000owned.pdf");
        document.setStatus('A');
        document.setUpdatedatetime(new Date(12345));
        document.setRestrictToProgram(false);
        when(documents.find(42)).thenReturn(document);
        when(documents.find(Integer.valueOf(42))).thenReturn(document);
        filings.add(link("demographic", 1001));
        when(links.findByDocumentNoAndModule(42, "demographic")).thenAnswer(call ->
                filings.stream().filter(link -> "demographic".equals(link.getId().getModule())).toList());
        when(links.getCtrlDocument(42)).thenAnswer(call -> filings.isEmpty() ? null : filings.getFirst());
        when(routing.findByLabNoAndLabType(42, "DOC")).thenReturn(routes);
        when(routing.findDocByDemographic(42)).thenReturn(routes);
        cachedDocumentDao = EDocUtil.class.getDeclaredField("documentDao");
        cachedDocumentDao.setAccessible(true);
        previousDocumentDao = cachedDocumentDao.get(null);
        cachedDocumentDao.set(null, documents);
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        previousIncomingDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        Path store = Files.createDirectory(root.resolve("stored"));
        Path incoming = Files.createDirectory(root.resolve("incoming"));
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", store.toString());
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incoming.toString());
        source = store.resolve(document.getDocfilename());
        destination = incoming.resolve("7/Refile/Rowned.pdf");
        Files.write(source, originalBytes);
        request = new MockHttpServletRequest("POST", "/casemgmt/NoteBrowserDocumentDelete");
        request.setParameter("demographicNo", "1001");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        ChartProgramContext.remember(request, "7");
        response = new MockHttpServletResponse();
        previousContext = ActionContext.getContext();
        ActionContext.of(new java.util.HashMap<>()).withServletRequest(request).withServletResponse(response).bind();
    }

    @AfterEach
    void restoreSharedProcessState() throws Exception {
        if (cachedDocumentDao != null) cachedDocumentDao.set(null, previousDocumentDao);
        restore("DOCUMENT_DIR", previousDocumentDir);
        restore("INCOMINGDOCUMENT_DIR", previousIncomingDir);
        if (previousContext == null) ActionContext.clear();
        else previousContext.bind();
    }

    static Stream<Arguments> deniedResources() {
        return Stream.of(Operation.values()).flatMap(operation -> Stream.of("record", "shared-record", "routed-record",
                "edoc-module", "edoc-patient", "foreign", "provider-link", "unfiled", "program", "source-queue", "missing")
                .map(boundary -> Arguments.of(operation, boundary)));
    }

    @ParameterizedTest
    @MethodSource("deniedResources")
    void shouldPreserveStoredRowsAndBytes_whenAnyResourceBoundaryIsDenied(Operation operation, String boundary) throws Exception {
        if (operation == Operation.UNDELETE) document.setStatus('D');
        switch (boundary) {
            case "record" -> when(security.isAllowedAccessToPatientRecord(login, 1001)).thenReturn(false);
            case "shared-record" -> {
                filings.add(link("demographic", 2002));
                when(security.isAllowedAccessToPatientRecord(login, 2002)).thenReturn(false);
            }
            case "routed-record" -> {
                routes.add(route(2002));
                when(security.isAllowedAccessToPatientRecord(login, 2002)).thenReturn(false);
            }
            case "edoc-module" -> when(security.hasPrivilege(login, "_edoc", "w", (String) null)).thenReturn(false);
            case "edoc-patient" -> when(security.hasPrivilege(login, "_edoc", "w", "1001")).thenReturn(false);
            case "foreign" -> { filings.clear(); filings.add(link("demographic", 2002)); }
            case "provider-link" -> { filings.clear(); filings.add(link("provider", 1001)); }
            case "unfiled" -> { filings.clear(); filings.add(link("demographic", 0)); }
            case "program" -> { document.setRestrictToProgram(true); document.setProgramId(9); }
            case "source-queue" -> {
                QueueDocumentLink queue = new QueueDocumentLink();
                queue.setDocId(42); queue.setQueueId(9); queue.setStatus("A");
                when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue));
            }
            case "missing" -> when(documents.find(42)).thenReturn(null);
            default -> throw new IllegalArgumentException(boundary);
        }
        char status = document.getStatus();
        Date updated = document.getUpdatedatetime();
        assertThatThrownBy(() -> action(operation).execute()).isInstanceOf(SecurityException.class);
        assertUnchanged(status, updated);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void shouldPerformTheNativeMutation_whenSharedPatientsAndStoredRestrictionsAreAllowed(Operation operation) throws Exception {
        filings.add(link("demographic", 2002));
        routes.add(route(3003));
        document.setRestrictToProgram(true);
        document.setProgramId(9);
        ProgramProvider domain = new ProgramProvider();
        domain.setProgramId(9L);
        when(programs.getProgramDomain(login, "4249")).thenReturn(List.of(domain));
        QueueDocumentLink queue = new QueueDocumentLink();
        queue.setDocId(42); queue.setQueueId(9); queue.setStatus("A");
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue));
        when(security.hasPrivilege(login, "_queue.9", "r", (String) null)).thenReturn(true);
        if (operation == Operation.UNDELETE) document.setStatus('D');
        assertThat(action(operation).execute()).isEqualTo("success");
        assertThat(Files.readAllBytes(source)).containsExactly(originalBytes);
        if (operation == Operation.REFILE) {
            assertThat(Files.readAllBytes(destination)).containsExactly(originalBytes);
            assertThat(document.getStatus()).isEqualTo('A');
            verify(documents, never()).merge(any(Document.class));
        } else {
            assertThat(document.getStatus()).isEqualTo(operation == Operation.DELETE ? 'D' : 'A');
            verify(documents).merge(document);
        }
    }

    @Test
    void shouldLeaveNoCopyOrMetadataChange_whenTheRefileDestinationIsNotAuthorized() throws Exception {
        when(security.hasPrivilege(login, "_queue.7", "r", (String) null)).thenReturn(false);
        assertThatThrownBy(() -> action(Operation.REFILE).execute()).isInstanceOf(SecurityException.class);
        assertUnchanged('A', document.getUpdatedatetime());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void shouldRejectBeforeClinicalLookups_whenTheRequestPatientIsMissing(Operation operation) throws Exception {
        // A fresh legacy request has no signed token that would reject it even earlier.
        MockHttpServletRequest missing = new MockHttpServletRequest("POST", request.getRequestURI());
        missing.setSession(request.getSession());
        ActionContext.getContext().withServletRequest(missing);
        assertThat(action(operation).execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        verify(documents, never()).find(anyInt());
        assertUnchanged('A', document.getUpdatedatetime());
    }

    private AbstractNoteBrowserDocumentMutation2Action action(Operation operation) {
        AbstractNoteBrowserDocumentMutation2Action action = switch (operation) {
            case DELETE -> { var a = new NoteBrowserDocumentDelete2Action(); a.setDelDocumentNo("42"); yield a; }
            case UNDELETE -> { var a = new NoteBrowserDocumentUndelete2Action(); a.setUndelDocumentNo("42"); yield a; }
            case REFILE -> { var a = new NoteBrowserDocumentRefile2Action(); a.setRefileDocumentNo("42"); a.setQueueId("7"); yield a; }
        };
        action.setDemographicNo("1001");
        return action;
    }

    private void assertUnchanged(char status, Date updated) throws Exception {
        assertThat(document.getStatus()).isEqualTo(status);
        assertThat(document.getUpdatedatetime()).isEqualTo(updated);
        assertThat(Files.readAllBytes(source)).containsExactly(originalBytes);
        assertThat(destination).doesNotExist();
        verify(documents, never()).merge(any(Document.class));
        verify(links, never()).persist(any(CtlDocument.class));
    }

    private CtlDocument link(String module, int patient) {
        CtlDocument link = new CtlDocument();
        link.setId(new CtlDocumentPK(module, patient, 42));
        link.setStatus("A");
        return link;
    }

    private PatientLabRouting route(int patient) {
        PatientLabRouting route = new PatientLabRouting();
        route.setLabNo(42); route.setLabType("DOC"); route.setDemographicNo(patient);
        return route;
    }

    private void restore(String key, String value) {
        if (value == null) CarlosProperties.getInstance().remove(key);
        else CarlosProperties.getInstance().setProperty(key, value);
    }
}
