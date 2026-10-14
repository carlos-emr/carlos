/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.gate;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.mds.gate.ViewSplit2Action;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class StoredDocumentViewAuthorizationUnitTest extends CarlosUnitTestBase {
    final SecurityInfoManager security = mock(SecurityInfoManager.class);
    final CtlDocumentDao links = mock(CtlDocumentDao.class);
    final PatientLabRoutingDao patients = mock(PatientLabRoutingDao.class);
    final QueueDocumentLinkDao queues = mock(QueueDocumentLinkDao.class);
    final DocumentDao documents = mock(DocumentDao.class);
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    MockedStatic<ServletActionContext> servlet;

    @BeforeEach void setup() {
        registerMock(SecurityInfoManager.class, security); registerMock(CtlDocumentDao.class, links);
        registerMock(PatientLabRoutingDao.class, patients); registerMock(QueueDocumentLinkDao.class, queues);
        registerMock(DocumentDao.class, documents);
        when(documents.find(42)).thenReturn(new io.github.carlos_emr.carlos.commn.model.Document());
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        LoggedInInfo info = new LoggedInInfo();
        var provider = new io.github.carlos_emr.carlos.commn.model.Provider(); provider.setProviderNo("999998");
        info.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        request.setParameter("segmentID", "42"); request.setParameter("document", "42");
        when(security.hasPrivilege(any(), eq("_lab"), eq("r"), isNull())).thenReturn(true);
        when(security.hasPrivilege(any(), eq("_edoc"), eq("r"), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
    }
    @AfterEach void cleanup() { servlet.close(); }
    String open(String view) throws Exception {
        return "split".equals(view) ? new ViewSplit2Action().execute() : new ViewStoredDocumentRead2Action().execute();
    }
    QueueDocumentLink queue(int number, String status) {
        QueueDocumentLink link = new QueueDocumentLink(); link.setQueueId(number); link.setStatus(status); return link;
    }

    @ParameterizedTest @ValueSource(strings = {"show", "split"})
    void deniedNamedQueuePreventsJspMetadataAndHashReads(String view) {
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(8, "A")));
        request.setParameter("queueID", "1"); // Caller cannot substitute a shared source queue.
        assertThatThrownBy(() -> open(view)).isInstanceOf(SecurityException.class);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(documents).find(42); verifyNoMoreInteractions(documents);
    }

    @ParameterizedTest @ValueSource(strings = {"show", "split"})
    void allLinkedPatientsMustPermitReadEvenWhenGlobalReadIsGranted(String view) {
        CtlDocument link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic", 7, 42));
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link));
        when(security.hasPrivilege(any(), eq("_edoc"), eq("r"), eq("7"))).thenReturn(false);
        assertThatThrownBy(() -> open(view)).isInstanceOf(SecurityException.class);
        assertThat(response.getStatus()).isEqualTo(403);
        verify(documents).find(42); verifyNoMoreInteractions(documents);
    }

    @ParameterizedTest @ValueSource(strings = {"show", "split"})
    void patientRoutingWithoutCtlLinkCannotBypassPatientChartAccess(String view) {
        PatientLabRouting route = new PatientLabRouting(); route.setDemographicNo(9);
        when(patients.findDocByDemographic(42)).thenReturn(List.of(route));
        when(security.isAllowedAccessToPatientRecord(any(), eq(9))).thenReturn(false);
        assertThatThrownBy(() -> open(view)).isInstanceOf(SecurityException.class);
        assertThat(response.getStatus()).isEqualTo(403);
        verify(documents).find(42); verifyNoMoreInteractions(documents);
    }

    @ParameterizedTest @ValueSource(strings = {"show", "split"})
    void globalReadIsRequiredButDocumentWriteIsNot(String view) throws Exception {
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(1, "A"), queue(8, "I")));
        assertThat(open(view)).isEqualTo(ActionSupport.SUCCESS);
        verify(security, never()).hasPrivilege(any(), anyString(), eq("w"), nullable(String.class));
        when(security.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(false);
        assertThatThrownBy(() -> open(view)).isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"show", "split"})
    void anAuthorizedNamedQueueAndUnfiledProviderDocumentRemainReadable(String view) throws Exception {
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(8, "A")));
        when(security.hasPrivilege(any(), eq("_queue.8"), eq("r"), isNull())).thenReturn(true);
        assertThat(open(view)).isEqualTo(ActionSupport.SUCCESS);
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test void actualShowDocumentRouteUsesThePatientAndQueueAwareGate() throws Exception {
        String mapping = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-document.xml"));
        assertThat(mapping).contains("<action name=\"documentManager/ViewShowDocument\" class=\""
                + ViewStoredDocumentRead2Action.class.getName() + "\">");
    }

    @ParameterizedTest @ValueSource(strings = {"show", "split"})
    void accessiblePatientCannotExposeDocumentRestrictedToAnotherProgram(String view) {
        var document = new io.github.carlos_emr.carlos.commn.model.Document();
        document.setRestrictToProgram(true); document.setProgramId(17);
        when(documents.find(42)).thenReturn(document);
        registerMock(io.github.carlos_emr.carlos.managers.ProgramManager2.class,
                mock(io.github.carlos_emr.carlos.managers.ProgramManager2.class));
        CtlDocument link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic", 7, 42));
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link));
        request.setParameter("programId", "18");
        assertThatThrownBy(() -> open(view)).isInstanceOf(SecurityException.class).hasMessageContaining("program");
        assertThat(response.getStatus()).isEqualTo(403);
        verify(documents).find(42); verifyNoMoreInteractions(documents);
    }
}
