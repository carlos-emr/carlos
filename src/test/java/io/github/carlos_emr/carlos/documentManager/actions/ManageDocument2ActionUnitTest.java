package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.commn.dao.CtlDocTypeDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Queue;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("action")
class ManageDocument2ActionUnitTest extends CarlosUnitTestBase {

    @Mock
    private DocumentDao documentDao;

    @Mock
    private io.github.carlos_emr.carlos.commn.dao.DemographicDao incomingDemographicDao;

    @Mock
    private QueueDao queueDao;

    @Mock
    private CtlDocumentDao ctlDocumentDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;

    @Mock
    private ProviderInboxRoutingDao providerInboxRoutingDao;

    @Mock
    private PatientLabRoutingDao patientLabRoutingDao;

    @Mock
    private ProgramManager2 programManager;

    @Mock
    private ProgramManager legacyProgramManager;

    @Mock
    private CaseManagementNoteLinkDAO caseManagementNoteLinkDao;

    @Mock
    private CaseManagementNoteDAO caseManagementNoteDao;

    @Mock
    private TicklerLinkDao ticklerLinkDao;

    @Mock
    private TicklerManager ticklerManager;

    @Mock
    private ProviderDao providerDao;

    @Mock
    private CtlDocTypeDao ctlDocTypeDao;

    @Mock
    private DemographicManager demographicManager;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private AutoCloseable mocks;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private TestManageDocument2Action action;
    private String previousIncomingDocumentDir;
    private String previousDocumentDir;
    private final MetadataTransactions metadataTransactions = new MetadataTransactions();

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        previousIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        outboundEmailArchiveDao = Mockito.mock(OutboundEmailArchiveDao.class);
        registerMock(DocumentDao.class, documentDao);
        Mockito.lenient().when(documentDao.find(42)).thenReturn(new Document());
        registerMock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class, incomingDemographicDao);
        registerMock(QueueDao.class, queueDao);
        registerMock(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class,
                Mockito.mock(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class));
        registerMock(CtlDocumentDao.class, ctlDocumentDao);
        // Default false: these tests are about path validation, not archive recognition, and an
        // unstubbed mock would answer false anyway. Registered explicitly so the reason is on the
        // record rather than relying on Mockito's default.
        registerMock(OutboundEmailArchiveDao.class, outboundEmailArchiveDao);
        registerMock(ProviderInboxRoutingDao.class, providerInboxRoutingDao);
        registerMock(PatientLabRoutingDao.class, patientLabRoutingDao);
        registerMock(ProgramManager2.class, programManager);
        registerMock(ProgramManager.class, legacyProgramManager);
        registerMock(CaseManagementNoteLinkDAO.class, caseManagementNoteLinkDao);
        registerMock(CaseManagementNoteDAO.class, caseManagementNoteDao);
        registerMock(TicklerLinkDao.class, ticklerLinkDao);
        registerMock(TicklerManager.class, ticklerManager);
        registerMock(ProviderDao.class, providerDao);
        registerMock(CtlDocTypeDao.class, ctlDocTypeDao);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        request = new MockHttpServletRequest();
        response = spy(new MockHttpServletResponse());
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        action = new TestManageDocument2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreProperty("INCOMINGDOCUMENT_DIR", previousIncomingDocumentDir);
        restoreProperty("DOCUMENT_DIR", previousDocumentDir);
        if (servletActionContext != null) {
            servletActionContext.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    private Document prepareMetadata() {
        authorizeEdocWrite();
        request.setMethod("POST"); request.setParameter("documentId", "42"); request.setParameter("demog", "10");
        request.setParameter("documentDescription", "Updated description"); request.setParameter("docType", "Lab");
        request.setParameter("observationDate", "2026-09-28");
        Document document = new Document(42); document.setRestrictToProgram(false); document.setStatus('A');
        when(documentDao.find(42)).thenReturn(document); when(documentDao.findForPageMutation(42)).thenReturn(document);
        when(ctlDocumentDao.getCtrlDocument(42)).thenReturn(patientLink(10));
        when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(10)));
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), eq("10"))).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), eq("20"))).thenReturn(true);
        when(providerInboxRoutingDao.removeLinkFromDocument(eq("DOC"), eq(42), anyString())).thenReturn(true);
        registerMock(org.springframework.transaction.PlatformTransactionManager.class, metadataTransactions);
        return document;
    }

    private void metadata(String route) {
        try (MockedStatic<EDocUtil> edoc = mockStatic(EDocUtil.class)) {
            if ("ajax".equals(route)) action.documentUpdateAjax();
            else if ("legacy".equals(route)) action.documentUpdate();
            else {request.removeParameter("method"); action.execute();}
        }
    }

    private void assertNoMetadataWrites() {
        verify(documentDao, Mockito.never()).merge(any(Document.class));
        verify(patientLabRoutingDao, Mockito.never()).persist(any());
        verify(ctlDocumentDao, Mockito.never()).persist(any());
        verify(ctlDocumentDao, Mockito.never()).remove(any(CtlDocumentPK.class));
        verifyNoInteractions(providerInboxRoutingDao);
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest @CsvSource({"ajax,GET", "ajax,HEAD", "legacy,GET", "legacy,HEAD", "fallback,GET", "fallback,HEAD"})
    void metadataRequiresPostEvenThroughNoMethodFallback(String route, String verb) {
        prepareMetadata(); request.setMethod(verb); metadata(route);
        assertThat(response.getStatus()).isEqualTo(405); assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertNoMetadataWrites(); verify(documentDao, Mockito.never()).findForPageMutation(anyInt());
    }

    @ParameterizedTest @CsvSource({"ajax,ctl", "legacy,ctl", "ajax,routing", "legacy,routing", "ajax,program", "legacy,program", "ajax,queue", "legacy,queue", "ajax,target", "legacy,target", "ajax,targetPrivilege", "legacy,targetPrivilege"})
    void metadataDeniesEverySourceScopeAndDestinationBeforeAnyWrite(String route, String denial) throws Exception {
        Document document = prepareMetadata();
        switch (denial) {
            case "ctl" -> {
                when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(10), patientLink(20)));
                when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(20))).thenReturn(false);
            }
            case "routing" -> {
                var linked = new io.github.carlos_emr.carlos.commn.model.PatientLabRouting(); linked.setDemographicNo(20);
                when(patientLabRoutingDao.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(linked));
                when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(20))).thenReturn(false);
            }
            case "program" -> {document.setRestrictToProgram(true); document.setProgramId(17);}
            case "queue" -> {
                var queues = Mockito.mock(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class);
                var link = new io.github.carlos_emr.carlos.commn.model.QueueDocumentLink(); link.setQueueId(7); link.setStatus("A");
                when(queues.getQueueFromDocument(42)).thenReturn(List.of(link));
                registerMock(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class, queues);
            }
            case "target" -> {request.setParameter("demog", "20"); when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(20))).thenReturn(false);}
            case "targetPrivilege" -> {request.setParameter("demog", "20"); when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), eq("20"))).thenReturn(false);}
            default -> throw new AssertionError();
        }
        metadata(route); assertThat(response.getStatus()).isEqualTo(403); assertNoMetadataWrites();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(json.path("accepted").asBoolean()).isFalse(); assertThat(json.path("document").asInt()).isEqualTo(42);
    }

    @ParameterizedTest @ValueSource(strings = {"ajax", "legacy"})
    void metadataRechecksProgramAfterWaitingForDocumentLock(String route) {
        Document document = prepareMetadata();
        when(documentDao.findForPageMutation(42)).thenAnswer(invocation -> {document.setRestrictToProgram(true); document.setProgramId(99); return document;});
        metadata(route); assertThat(response.getStatus()).isEqualTo(403); assertNoMetadataWrites();
        assertThat(metadataTransactions.rollbacks).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"ajax", "legacy"})
    void metadataRejectsDuplicateTargetsAndMalformedProviderLists(String route) {
        prepareMetadata(); request.setParameter("demog", new String[]{"10", "20"}); metadata(route);
        assertThat(response.getStatus()).isEqualTo(403); assertNoMetadataWrites();
        response.reset(); request.setParameter("demog", "10"); request.setParameter("flagproviders", new String[]{"999998", "bad provider"}); metadata(route);
        assertThat(response.getStatus()).isEqualTo(403); assertNoMetadataWrites();
    }

    @ParameterizedTest @ValueSource(strings = {"ajax", "legacy"})
    void repeatedMetadataClassificationSaveDoesNotDuplicateDocRouting(String route) throws Exception {
        Document document = prepareMetadata();
        var routes = new java.util.ArrayList<io.github.carlos_emr.carlos.commn.model.PatientLabRouting>();
        when(patientLabRoutingDao.findByLabNoAndLabType(42, "DOC")).thenReturn(routes);
        Mockito.doAnswer(invocation -> {routes.add(invocation.getArgument(0)); return null;}).when(patientLabRoutingDao).persist(any());
        metadata(route); response.reset(); request.setParameter("docType", "Consult"); metadata(route);
        assertThat(response.getStatus()).isEqualTo(200); assertThat(document.getDoctype()).isEqualTo("Consult");
        assertThat(routes).hasSize(1); assertThat(routes.get(0).getLabType()).isEqualTo("DOC");
        assertThat(routes.get(0).getDemographicNo()).isEqualTo(10);
        verify(patientLabRoutingDao, Mockito.never()).findByLabNoAndLabType(42, "Lab");
        verify(patientLabRoutingDao, Mockito.never()).findByLabNoAndLabType(42, "Consult");
        assertThat(metadataTransactions.commits).isEqualTo(2);
        if ("ajax".equals(route)) {
            var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
            assertThat(data.path("success").asBoolean()).isTrue(); assertThat(data.path("accepted").asBoolean()).isTrue();
            assertThat(data.path("document").asInt()).isEqualTo(42); assertThat(data.path("patientId").asText()).isEqualTo("10");
        }
    }

    @ParameterizedTest @CsvSource({"ajax,0", "legacy,0", "ajax,-1", "legacy,-1", "ajax,provider", "legacy,provider"})
    void metadataOnlyUnfiledAndProviderCasesNeverCreateOrRewritePatientLinks(String route, String target) {
        prepareMetadata();
        if ("provider".equals(target)) {
            CtlDocument link = new CtlDocument(); link.setId(new CtlDocumentPK("provider", 999998, 42));
            when(ctlDocumentDao.getCtrlDocument(42)).thenReturn(link); request.setParameter("demog", "999998");
        } else request.setParameter("demog", target);
        metadata(route); assertThat(response.getStatus()).isEqualTo(200);
        verify(patientLabRoutingDao, Mockito.never()).persist(any()); verify(ctlDocumentDao, Mockito.never()).persist(any());
        verify(ctlDocumentDao, Mockito.never()).remove(any(CtlDocumentPK.class));
    }

    @ParameterizedTest @ValueSource(strings = {"ajax", "legacy"})
    void routingFailureRollsBackAndUnknownCommitNeverClaimsUnaccepted(String route) throws Exception {
        prepareMetadata(); request.setParameter("flagproviders", "999998");
        doThrow(new IllegalStateException("routing failed")).when(providerInboxRoutingDao).addToProviderInboxStrict("999998", 42, "DOC");
        metadata(route); assertThat(response.getStatus()).isEqualTo(500); assertThat(metadataTransactions.rollbacks).isEqualTo(1);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(mapper.readTree(response.getContentAsString()).path("accepted").asBoolean()).isFalse();
        response.reset(); request.removeParameter("flagproviders"); metadataTransactions.failCommit = true;
        metadata(route); assertThat(response.getStatus()).isEqualTo(500);
        assertThat(mapper.readTree(response.getContentAsString()).path("accepted").asBoolean()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "HEAD"})
    void providerUnlinkRequiresPost(String verb) {
        prepareMetadata(); request.setMethod(verb); action.removeLinkFromDocument();
        assertThat(response.getStatus()).isEqualTo(405); assertNoMetadataWrites();
    }

    @Test void providerUnlinkChecksTypeAndActualSourceBeforeRemovingAnything() throws Exception {
        Document document = prepareMetadata(); request.setParameter("docId", "42"); request.setParameter("providerNo", "999998");
        request.setParameter("docType", "HL7"); action.removeLinkFromDocument();
        assertThat(response.getStatus()).isEqualTo(403); verifyNoInteractions(providerInboxRoutingDao);
        response.reset(); request.setParameter("docType", "DOC"); document.setRestrictToProgram(true); document.setProgramId(99);
        action.removeLinkFromDocument(); assertThat(response.getStatus()).isEqualTo(403); verifyNoInteractions(providerInboxRoutingDao);
        response.reset(); document.setRestrictToProgram(false); action.removeLinkFromDocument();
        assertThat(response.getStatus()).isEqualTo(200); verify(providerInboxRoutingDao).removeLinkFromDocument("DOC", 42, "999998");
        var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(data.path("success").asBoolean()).isTrue(); assertThat(data.path("document").asInt()).isEqualTo(42);
        assertThat(data.path("linkedProviders").isArray()).isTrue();
    }

    @Test void providerUnlinkFailureCannotClaimSuccess() throws Exception {
        prepareMetadata(); request.setParameter("docId", "42"); request.setParameter("providerNo", "999998"); request.setParameter("docType", "DOC");
        when(providerInboxRoutingDao.removeLinkFromDocument("DOC", 42, "999998")).thenReturn(false);
        action.removeLinkFromDocument();
        assertThat(response.getStatus()).isEqualTo(500); assertThat(metadataTransactions.rollbacks).isEqualTo(1);
        var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(data.path("success").asBoolean()).isFalse(); assertThat(data.path("accepted").asBoolean()).isFalse();
    }

    @Test void providerUnlinkResponseExcludesRetainedAuditRows() throws Exception {
        prepareMetadata(); request.setParameter("docId", "42"); request.setParameter("providerNo", "999998"); request.setParameter("docType", "DOC");
        var removed = new io.github.carlos_emr.carlos.commn.model.ProviderInboxItem();
        removed.setProviderNo("999998"); removed.setStatus("X");
        var remaining = new io.github.carlos_emr.carlos.commn.model.ProviderInboxItem();
        remaining.setProviderNo("999997"); remaining.setStatus("N");
        when(providerInboxRoutingDao.getProvidersWithRoutingForDocument("DOC", 42)).thenReturn(List.of(removed, remaining));
        action.removeLinkFromDocument();
        assertThat(response.getStatus()).isEqualTo(200);
        var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(data.path("linkedProviders")).hasSize(1);
        assertThat(data.path("linkedProviders").get(0).path("providerNo").asText()).isEqualTo("999997");
        assertThat(removed.getStatus()).isEqualTo("X");
    }

    private static final class MetadataTransactions extends org.springframework.transaction.support.AbstractPlatformTransactionManager {
        int commits, rollbacks; boolean failCommit;
        @Override protected Object doGetTransaction() {return new Object();}
        @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) { }
        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
            if (failCommit) throw new org.springframework.transaction.TransactionSystemException("Unconfirmed commit"); commits++;
        }
        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {rollbacks++;}
    }

    @Test
    void shouldReturnNoneAndSendError_whenDirectResponseHandlerFailsBeforeCommit() throws Exception {
        request.setParameter("method", "viewDocumentInfo");
        doThrow(new IllegalStateException("writer failed")).when(response).getWriter();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldReturnNone_whenDirectResponseHandlerFailsAfterCommit() {
        request.setParameter("method", "viewDocumentInfo");
        CommittedFailingResponse committedResponse = new CommittedFailingResponse();
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(committedResponse);
        action = new TestManageDocument2Action();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(action.getActionErrors()).isEmpty();
    }

    @Test
    void shouldReturnNoneAndSendForbidden_whenDirectResponseHandlerDeniesAuthorization() {
        request.setParameter("method", "display");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(action.getActionErrors()).isEmpty();
    }


    @Test
    void shouldReturnNoneAndSendForbidden_whenNonDirectHandlerDeniesAuthorization() {
        request.setParameter("method", "refileDocumentAjax");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(action.getActionErrors()).isEmpty();
    }

    @Test
    void shouldSendServerErrorAndKeepAjaxFailed_whenRefileCopyFails() {
        authorizeEdocWrite();
        request.setMethod("POST");
        request.setParameter("method", "refileDocumentAjax");
        request.setParameter("documentId", "42");
        request.setParameter("queueId", "7");
        when(securityInfoManager.hasPrivilege(any(), eq("_queue.7"), eq("r"), isNull())).thenReturn(true);
        Document document = new Document();
        document.setDocfilename("stored.pdf");
        when(documentDao.find(42)).thenReturn(document);
        when(queueDao.find(7)).thenReturn(new Queue());

        try (MockedStatic<EDocUtil> edocUtil = mockStatic(EDocUtil.class)) {
            edocUtil.when(() -> EDocUtil.refileDocument("42", "7"))
                    .thenThrow(new IOException("destination exists"));

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            edocUtil.verify(() -> EDocUtil.refileDocument("42", "7"));
        }
    }

    @Test
    void shouldDenyRefileDestinationBeforeReadingDocument() {
        authorizeEdocWrite();
        request.setMethod("POST");
        request.setParameter("method", "refileDocumentAjax");
        request.setParameter("documentId", "42");
        request.setParameter("queueId", "2");
        try (MockedStatic<EDocUtil> edocUtil = mockStatic(EDocUtil.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            edocUtil.verifyNoInteractions();
        }
        verifyNoInteractions(documentDao, ctlDocumentDao, queueDao);
    }

    @Test
    void shouldDenyRefileSourcePatientBeforeReadingDocumentBytes() {
        authorizeEdocWrite();
        request.setMethod("POST");
        request.setParameter("method", "refileDocumentAjax");
        request.setParameter("documentId", "42");
        request.setParameter("queueId", "1");
        when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(100)));
        try (MockedStatic<EDocUtil> edocUtil = mockStatic(EDocUtil.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            edocUtil.verifyNoInteractions();
        }
        verifyNoInteractions(documentDao, queueDao);
    }

    @Test
    void shouldRejectInvalidIdentifiers_whenRefilingDocument() {
        authorizeEdocWrite();
        request.setMethod("POST");
        request.setParameter("method", "refileDocumentAjax");
        request.setParameter("documentId", "42");
        request.setParameter("queueId", "../other");

        try (MockedStatic<EDocUtil> edocUtil = mockStatic(EDocUtil.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            edocUtil.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectNonPostMethod_whenRefilingDocument(String method) {
        authorizeEdocWrite();
        request.setMethod(method);
        request.setParameter("method", "refileDocumentAjax");
        request.setParameter("documentId", "42");
        request.setParameter("queueId", "7");
        when(securityInfoManager.hasPrivilege(any(), eq("_queue.7"), eq("r"), isNull())).thenReturn(true);

        try (MockedStatic<EDocUtil> edocUtil = mockStatic(EDocUtil.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            edocUtil.verifyNoInteractions();
        }
    }

    @Test
    void shouldRejectMissingQueue_whenRefilingDocument() {
        authorizeEdocWrite();
        request.setMethod("POST");
        request.setParameter("method", "refileDocumentAjax");
        request.setParameter("documentId", "42");
        request.setParameter("queueId", "7");
        when(securityInfoManager.hasPrivilege(any(), eq("_queue.7"), eq("r"), isNull())).thenReturn(true);
        Document document = new Document();
        document.setDocfilename("stored.pdf");
        when(documentDao.find(42)).thenReturn(document);
        when(queueDao.find(7)).thenReturn(null);

        try (MockedStatic<EDocUtil> edocUtil = mockStatic(EDocUtil.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            edocUtil.verifyNoInteractions();
        }
    }

    @Test
    void shouldRefuseArchiveDocument_beforeDocumentUpdateMutation() {
        authorizeEdocWrite();
        request.setMethod("POST");
        request.setParameter("method", "documentUpdate");
        request.setParameter("documentId", "42");
        when(outboundEmailArchiveDao.existsByDocumentNo(42)).thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verify(providerInboxRoutingDao, never()).addToProviderInbox(anyString(), anyInt(), anyString());
        verify(documentDao, never()).getDocument(anyString());
        verify(documentDao, never()).merge(any(Document.class));
        verify(ctlDocumentDao, never()).getCtrlDocument(anyInt());
    }

    @Test
    void shouldRejectDocumentWithoutStoredFile_whenRefilingDocument() {
        authorizeEdocWrite();
        request.setMethod("POST");
        request.setParameter("method", "refileDocumentAjax");
        request.setParameter("documentId", "42");
        request.setParameter("queueId", "7");
        when(securityInfoManager.hasPrivilege(any(), eq("_queue.7"), eq("r"), isNull())).thenReturn(true);
        when(documentDao.find(42)).thenReturn(new Document());

        try (MockedStatic<EDocUtil> edocUtil = mockStatic(EDocUtil.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            edocUtil.verifyNoInteractions();
        }
    }

    @Test
    void shouldReturnNoneAndSendForbidden_whenShowPageDeniesAuthorization() {
        request.setParameter("method", "showPage");
        request.setParameter("page", "1");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(action.getActionErrors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "showPage", "view", "viewDocPage", "display", "viewDocumentInfo",
            "viewDocumentDescription", "viewAnnotationAcknowledgementTickler" })
    void shouldDenyEveryDocumentReadBeforeMetadata_whenLinkedPatientIsRestricted(String method) throws Exception {
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        request.setParameter("method", method);
        request.setParameter("page", "1");
        request.setParameter("doc_no", "42");
        when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic"))
                .thenReturn(List.of(patientLink(10)));

        try (MockedStatic<EDocUtil> metadata = mockStatic(EDocUtil.class);
             MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            Mockito.verifyNoInteractions(documentDao);
            metadata.verifyNoInteractions();
            paths.verifyNoInteractions();
            verify(ctlDocumentDao, Mockito.never()).getCtrlDocument(anyInt());
            verify(securityInfoManager).isAllowedAccessToPatientRecord(any(), eq(10));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "showPage", "view", "viewDocPage", "display", "viewDocumentInfo",
            "viewDocumentDescription", "viewAnnotationAcknowledgementTickler" })
    void shouldDenyEveryDirectRead_whenOnlyDocRoutingLinksAnInaccessiblePatient(String method) throws Exception {
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        var route = new io.github.carlos_emr.carlos.commn.model.PatientLabRouting();
        route.setDemographicNo(20); route.setLabType("DOC");
        when(patientLabRoutingDao.findDocByDemographic(42)).thenReturn(List.of(route));
        request.setParameter("method", method); request.setParameter("page", "1"); request.setParameter("doc_no", "42");
        request.setParameter("demoNo", "10"); // A caller's different patient cannot authorize this routing.
        try (MockedStatic<EDocUtil> metadata = mockStatic(EDocUtil.class);
             MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(response.getContentAsByteArray()).isEmpty();
            metadata.verifyNoInteractions(); paths.verifyNoInteractions(); logActionMock.verifyNoInteractions();
            verify(documentDao, Mockito.never()).getDocument(anyString());
            verify(securityInfoManager).isAllowedAccessToPatientRecord(any(), eq(20));
        }
    }

    @Test
    void allowedCtlPatientCannotOverrideADifferentDeniedDocRoutingPatient() {
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(10)));
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(10))).thenReturn(true);
        var route = new io.github.carlos_emr.carlos.commn.model.PatientLabRouting();
        route.setDemographicNo(20); route.setLabType("DOC");
        when(patientLabRoutingDao.findDocByDemographic(42)).thenReturn(List.of(route));
        request.setParameter("method", "showPage"); request.setParameter("page", "1"); request.setParameter("doc_no", "42");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(documentDao, Mockito.never()).getDocument(anyString());
        verify(securityInfoManager).isAllowedAccessToPatientRecord(any(), eq(20));
    }

    @Test
    void routedChartAccessWithoutPatientEdocReadCannotExposeCachedBytes() {
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(20))).thenReturn(true);
        var route = new io.github.carlos_emr.carlos.commn.model.PatientLabRouting();
        route.setDemographicNo(20); route.setLabType("DOC");
        when(patientLabRoutingDao.findDocByDemographic(42)).thenReturn(List.of(route));
        request.setParameter("method", "showPage"); request.setParameter("page", "1"); request.setParameter("doc_no", "42");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(securityInfoManager).hasPrivilege(any(), eq("_edoc"), eq("r"), eq("20"));
        verify(documentDao, Mockito.never()).getDocument(anyString());
    }

    @Test
    void directImageCannotBypassTheViewersPersistedNamedQueueGate() {
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        var queues = Mockito.mock(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class);
        registerMock(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class, queues);
        var queue = new io.github.carlos_emr.carlos.commn.model.QueueDocumentLink(); queue.setQueueId(17); queue.setStatus("A");
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue));
        request.setParameter("method", "showPage"); request.setParameter("page", "1"); request.setParameter("doc_no", "42");
        request.setParameter("queueId", "1");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(documentDao, Mockito.never()).getDocument(anyString());
    }

    @Test
    void authorizedDocRoutingServesCacheButAnIndependentSessionCannotReuseIt() throws Exception {
        byte[] cached = new byte[] {3, 7, 11};
        Files.write(tempDir.resolve("fixture.pdf_1.png"), cached);
        Document document = new Document(); document.setDocfilename("fixture.pdf");
        when(documentDao.getDocument("42")).thenReturn(document);
        var route = new io.github.carlos_emr.carlos.commn.model.PatientLabRouting();
        route.setDemographicNo(20); route.setLabType("DOC");
        when(patientLabRoutingDao.findDocByDemographic(42)).thenReturn(List.of(route));
        authorizeEdocWrite();
        LoggedInInfo allowed = LoggedInInfo.getLoggedInInfoFromSession(request);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(allowed, "_edoc", "r", "20")).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(allowed, 20)).thenReturn(true);
        request.setParameter("method", "showPage"); request.setParameter("page", "1"); request.setParameter("doc_no", "42");
        try (MockedStatic<PathValidationUtils> paths = cachePaths()) {
            action.execute();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsByteArray()).isEqualTo(cached);
            request = new MockHttpServletRequest(); response = new MockHttpServletResponse();
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
            LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
            request.setParameter("method", "showPage"); request.setParameter("page", "1"); request.setParameter("doc_no", "42");
            action = new TestManageDocument2Action(); action.execute();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            verify(documentDao, Mockito.times(1)).getDocument("42");
        }
    }

    @Test
    void shouldCheckEveryPatientLink_whenOneLinkedPatientIsAllowedAndAnotherIsRestricted() {
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(10))).thenReturn(true);
        when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic"))
                .thenReturn(List.of(patientLink(10), patientLink(20)));
        request.setParameter("method", "showPage");
        request.setParameter("page", "1");
        request.setParameter("doc_no", "42");
        request.setParameter("demoNo", "10");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(403);
        Mockito.verifyNoInteractions(documentDao);
        verify(securityInfoManager).isAllowedAccessToPatientRecord(any(), eq(20));
    }

    @ParameterizedTest
    @ValueSource(strings = { "showPage", "view", "viewDocPage", "display", "viewDocumentInfo",
            "viewDocumentDescription", "viewAnnotationAcknowledgementTickler" })
    void shouldDenyEveryDocumentReadBeforeCacheOrMetadata_whenPersistedProgramIsRestricted(String method) throws Exception {
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(10))).thenReturn(true);
        when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(10)));
        Document restricted = new Document(); restricted.setRestrictToProgram(true); restricted.setProgramId(17);
        when(documentDao.find(42)).thenReturn(restricted);
        request.setParameter("method", method); request.setParameter("page", "1"); request.setParameter("doc_no", "42");
        request.setParameter("programId", "18");
        try (MockedStatic<EDocUtil> metadata = mockStatic(EDocUtil.class);
             MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsByteArray()).isEmpty();
            metadata.verifyNoInteractions(); paths.verifyNoInteractions();
            verify(documentDao, Mockito.never()).getDocument(anyString());
        }
    }

    @Test
    void shouldRejectSecondSessionBeforeSharedCacheLookup_whenFirstSessionMayReadPatient() throws Exception {
        byte[] cached = new byte[] { 1, 2, 3, 4 };
        Files.write(tempDir.resolve("fixture.pdf_1.png"), cached);
        Document document = new Document();
        document.setDocfilename("fixture.pdf");
        when(documentDao.getDocument("42")).thenReturn(document);
        when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic"))
                .thenReturn(List.of(patientLink(10)));
        authorizeEdocWrite();
        LoggedInInfo first = LoggedInInfo.getLoggedInInfoFromSession(request);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(first, "_edoc", "r", "10")).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(first, 10)).thenReturn(true);
        request.setParameter("method", "showPage");
        request.setParameter("page", "1");
        request.setParameter("doc_no", "42");
        try (MockedStatic<PathValidationUtils> paths = cachePaths()) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsByteArray()).isEqualTo(cached);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");

            request = new MockHttpServletRequest();
            response = new MockHttpServletResponse();
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
            LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
            request.setParameter("method", "showPage");
            request.setParameter("page", "1");
            request.setParameter("doc_no", "42");
            action = new TestManageDocument2Action();
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsByteArray()).isEmpty();
            verify(documentDao, Mockito.times(1)).getDocument("42");
        }
    }

    @Test
    void shouldKeepProviderAndUnfiledDocumentsReadable_whenNoPositivePatientLinksExist() throws Exception {
        Files.write(tempDir.resolve("fixture.pdf_1.png"), new byte[] { 9 });
        Document document = new Document();
        document.setDocfilename("fixture.pdf");
        when(documentDao.getDocument("42")).thenReturn(document);
        when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic"))
                .thenReturn(List.of(patientLink(0), patientLink(-1)));
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        request.setParameter("method", "showPage");
        request.setParameter("page", "1");
        request.setParameter("doc_no", "42");
        try (MockedStatic<PathValidationUtils> paths = cachePaths()) {
            action.execute();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsByteArray()).containsExactly((byte) 9);
            verify(securityInfoManager, Mockito.never()).isAllowedAccessToPatientRecord(any(), anyInt());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "showPage", "viewDocPage" })
    void shouldReturnRetryableBusyStatus_whenUncachedImageCannotAcquireGlobalWorker(String method) throws Exception {
        Document document = new Document();
        document.setDocfilename("fixture.pdf");
        document.setContenttype("application/pdf");
        when(documentDao.getDocument("42")).thenReturn(document);
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        request.setParameter("method", method);
        request.setParameter("page", "1");
        request.setParameter("doc_no", "42");
        try (MockedStatic<PathValidationUtils> paths = cachePaths();
             MockedStatic<BoundedPdfTask> workers = mockStatic(BoundedPdfTask.class)) {
            workers.when(() -> BoundedPdfTask.runWithin(anyInt(), anyString(), any()))
                    .thenThrow(new BoundedPdfTask.BusyException());
            action.execute();
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getHeader("Retry-After")).isEqualTo("1");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "showPage", "view", "viewDocPage", "display" })
    void shouldReturnNotFoundWithoutInspectingFiles_whenRequestedDocumentWasDeleted(String method) throws Exception {
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), eq("10"))).thenReturn(true);
        when(ctlDocumentDao.findByDocumentNoAndModule(42, "demographic"))
                .thenReturn(List.of(patientLink(10)));
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(10))).thenReturn(true);
        request.setParameter("method", method);
        request.setParameter("page", "1");
        request.setParameter("doc_no", "42");

        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            verify(documentDao).getDocument("42");
            verify(securityInfoManager).isAllowedAccessToPatientRecord(any(), eq(10));
            paths.verifyNoInteractions();
        }
    }

    @Test
    void shouldStreamIncomingRasterFromConfiguredRoot_withoutDependingOnFiledDocumentDirectory() throws Exception {
        Path incoming = Files.createDirectory(tempDir.resolve("incoming"));
        Path queue = Files.createDirectories(incoming.resolve("1/Fax"));
        byte[] bytes = new byte[] { 7, 8, 9 };
        Files.write(queue.resolve("scan.png"), bytes);
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incoming.toString());
        authorizeIncomingPreview("scan.png");

        action.execute();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getContentAsByteArray()).isEqualTo(bytes);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void shouldReadOnlyIncomingCacheFiles_whenRendererReturnsAPath(boolean insideIncomingRoot) throws Exception {
        Path incoming = Files.createDirectory(tempDir.resolve("incoming"));
        Path cache = Files.createDirectories(incoming.resolve("1/Fax_cache"));
        Path supplied = insideIncomingRoot ? cache.resolve("scan_1.png") : tempDir.resolve("other-patient.png");
        byte[] bytes = new byte[] { 4, 5, 6 };
        Files.write(supplied, bytes);
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incoming.toString());
        authorizeIncomingPreview("scan.pdf");
        TestManageDocument2Action preview = spy(action);
        Mockito.doReturn(supplied.toFile()).when(preview).createIncomingCacheVersion("1", "Fax", "scan.pdf", 1);

        preview.execute();

        assertThat(response.getStatus()).isEqualTo(insideIncomingRoot ? 200 : 500);
        assertThat(response.getContentAsByteArray()).isEqualTo(insideIncomingRoot ? bytes : new byte[0]);
    }

    @Test
    void shouldRejectIncomingSymlinkOutsideConfiguredRoot_beforeReturningPatientBytes() throws Exception {
        Path incoming = Files.createDirectory(tempDir.resolve("incoming"));
        Path queue = Files.createDirectories(incoming.resolve("1/Fax"));
        Path outside = tempDir.resolve("other-patient.png");
        Files.write(outside, new byte[] { 1, 2, 3 });
        Files.createSymbolicLink(queue.resolve("scan.png"), outside);
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incoming.toString());
        authorizeIncomingPreview("scan.png");

        action.execute();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({ "viewIncomingDocPageAsPdf,GET", "viewIncomingDocPageAsImage,GET",
            "viewIncomingDocPageAsPdf,POST", "viewIncomingDocPageAsImage,POST",
            "viewIncomingDocPageAsPdf,get", "viewIncomingDocPageAsImage,GeT" })
    void shouldWaitOnlyForUnacceptedIncomingGet_whenParserCapacityIsFull(String method, String httpMethod) throws Exception {
        createIncomingPdf(new org.apache.pdfbox.pdmodel.common.PDRectangle(120, 160));
        authorizeIncomingPreview("scan.pdf");
        request.setParameter("method", method);
        request.setMethod(httpMethod);
        try (MockedStatic<BoundedPdfTask> workers = mockStatic(BoundedPdfTask.class)) {
            workers.when(() -> BoundedPdfTask.runWithin(anyInt(), anyString(), any()))
                    .thenThrow(new BoundedPdfTask.BusyException());
            action.execute();
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getHeader("Retry-After")).isEqualTo("1");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            if ("GET".equals(httpMethod)) {
                assertThat(response.getContentAsString()).contains("incomingDocumentCapacityWait", "incomingDocumentCapacityWait.js");
            } else {
                assertThat(response.getContentAsString()).doesNotContain("<script", "incomingDocumentCapacityWait");
            }
        }
    }

    @Test
    void shouldEncodeIncomingCapacityScriptAttribute_whenContextContainsMarkup() throws Exception {
        createIncomingPdf(new org.apache.pdfbox.pdmodel.common.PDRectangle(120, 160));
        authorizeIncomingPreview("scan.pdf");
        request.setMethod("GET");
        request.setParameter("method", "viewIncomingDocPageAsPdf");
        request.setContextPath("/carlos\"><script>alert(1)</script><img onerror=\"alert(2)");
        try (MockedStatic<BoundedPdfTask> workers = mockStatic(BoundedPdfTask.class)) {
            workers.when(() -> BoundedPdfTask.runWithin(anyInt(), anyString(), any()))
                    .thenThrow(new BoundedPdfTask.BusyException());
            action.execute();
            assertThat(response.getStatus()).isEqualTo(503);
            org.jsoup.nodes.Document html = org.jsoup.Jsoup.parse(response.getContentAsString());
            assertThat(html.select("script")).hasSize(1);
            assertThat(html.selectFirst("script").attr("src"))
                    .isEqualTo(request.getContextPath() + "/js/incomingDocumentCapacityWait.js");
            assertThat(html.selectFirst("script").data()).isEmpty();
            assertThat(html.select("img, [onerror]")).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "viewIncomingDocPageAsPdf", "viewIncomingDocPageAsImage" })
    void shouldReportCorruptIncomingPdfAsFailureWithoutCapacityRetry(String method) throws Exception {
        Path file = createIncomingPdf(new org.apache.pdfbox.pdmodel.common.PDRectangle(120, 160));
        Files.writeString(file, "not a PDF");
        authorizeIncomingPreview("scan.pdf");
        request.setMethod("GET");
        request.setParameter("method", method);
        action.execute();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader("Retry-After")).isNull();
        assertThat(response.getContentAsString()).doesNotContain("incomingDocumentCapacityWait");
    }

    @Test
    void shouldExtractRequestedIncomingPdfPage_beforeWritingServletResponse() throws Exception {
        createIncomingPdf(new org.apache.pdfbox.pdmodel.common.PDRectangle(120, 160));
        authorizeIncomingPreview("scan.pdf");
        request.setParameter("method", "viewIncomingDocPageAsPdf");
        request.setParameter("curPage", "2");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument extracted = org.apache.pdfbox.Loader.loadPDF(response.getContentAsByteArray())) {
            assertThat(extracted.getNumberOfPages()).isEqualTo(1);
            assertThat(extracted.getPage(0).getMediaBox().getWidth()).isEqualTo(240f);
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(extracted)).contains("Second page");
        }
    }

    @Test
    void shouldResetBinaryChannelBeforeErrorHtml_whenIncomingPdfStreamingFailsBeforeCommit() throws Exception {
        createIncomingPdf(new org.apache.pdfbox.pdmodel.common.PDRectangle(120, 160));
        authorizeIncomingPreview("scan.pdf");
        request.setParameter("method", "viewIncomingDocPageAsPdf");
        response = new FailingBinaryResponse();
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        action = new TestManageDocument2Action();

        action.execute();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(response.getContentAsString()).isNotEmpty().doesNotContain("%PDF-");
    }

    @Test
    void shouldNeverWriteLatePdfBytes_whenExtractionFinishesAfterCallerTimeout() throws Exception {
        createIncomingPdf(new org.apache.pdfbox.pdmodel.common.PDRectangle(120, 160));
        authorizeIncomingPreview("scan.pdf");
        request.setParameter("method", "viewIncomingDocPageAsPdf");
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Callable<?>> delayed = new java.util.concurrent.atomic.AtomicReference<>();
        try (MockedStatic<BoundedPdfTask> workers = mockStatic(BoundedPdfTask.class)) {
            workers.when(() -> BoundedPdfTask.runWithin(anyInt(), anyString(), any())).thenAnswer(invocation -> {
                delayed.set(invocation.getArgument(2));
                throw new IOException("simulated execution timeout");
            });
            action.execute();
        }
        assertThat(response.getStatus()).isEqualTo(500);
        byte[] failure = response.getContentAsByteArray();
        assertThat(delayed.get()).isNotNull();
        assertThatThrownBy(() -> delayed.get().call()).isInstanceOf(IOException.class);
        assertThat(response.getContentAsByteArray()).isEqualTo(failure);
        assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
    }

    @ParameterizedTest
    @CsvSource({ "14400,14400", "36000000,0.001" })
    void shouldRejectOversizedIncomingRasterBeforeAllocatingPixels(float width, float height) throws Exception {
        createIncomingPdf(new org.apache.pdfbox.pdmodel.common.PDRectangle(width, height));
        authorizeIncomingPreview("scan.pdf");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(Files.exists(tempDir.resolve("incoming/1/Fax_cache/scan_1.png"))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "612,792,96,true", "612,792,192,true", "14400,14400,96,false",
            "36000000,0.001,96,false", "0.001,36000000,96,false", "0,792,96,false",
            "NaN,792,96,false", "Infinity,792,96,false", "612,792,0,false", "0.001,0.001,96,true" })
    void shouldBoundActualRasterAllocation_whenPageDimensionsAreExtreme(float width, float height, int dpi, boolean allowed) {
        org.apache.pdfbox.pdmodel.common.PDRectangle box = Mockito.mock(org.apache.pdfbox.pdmodel.common.PDRectangle.class);
        when(box.getWidth()).thenReturn(width);
        when(box.getHeight()).thenReturn(height);
        assertThat(ManageDocument2Action.isRenderWithinPixelLimit(box, dpi)).isEqualTo(allowed);
    }

    @Test
    void shouldDiscardLateExtraction_whenTimedOutCallerAlreadyClosedItsResult() throws Exception {
        ManageDocument2Action.IncomingPdfPage result = new ManageDocument2Action.IncomingPdfPage();
        result.close();
        Path late = Files.writeString(tempDir.resolve("late.pdf"), "completed after timeout");
        assertThatThrownBy(() -> result.publish(late)).isInstanceOf(IOException.class);
        assertThat(Files.exists(late)).isFalse();
        assertThat(result.path()).isNull();
        result.close();
    }

    @Test
    void shouldDeleteCompletedExtraction_whenResponseStreamingFinishes() throws Exception {
        Path completed = Files.writeString(tempDir.resolve("completed.pdf"), "completed");
        try (ManageDocument2Action.IncomingPdfPage result = new ManageDocument2Action.IncomingPdfPage()) {
            result.publish(completed);
            assertThat(result.path()).isEqualTo(completed);
            assertThat(Files.exists(completed)).isTrue();
        }
        assertThat(Files.exists(completed)).isFalse();
    }

    @Test
    @Tag("integration")
    void shouldPublishOnlyCompleteIncomingImages_whenConcurrentUsersRenderSamePage() throws Exception {
        createIncomingPdf(new org.apache.pdfbox.pdmodel.common.PDRectangle(120, 160));
        File cache = action.createIncomingCacheVersion("1", "Fax", "scan.pdf", 1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(3);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.List<java.util.concurrent.Future<?>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < 8; j++) action.createIncomingCacheVersion("1", "Fax", "scan.pdf", 1);
                    return null;
                }));
            }
            tasks.add(executor.submit(() -> {
                start.await();
                for (int i = 0; i < 80; i++) {
                    java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(cache);
                    assertThat(image).as("the shared cache must never expose a partial PNG").isNotNull();
                    assertThat(image.getWidth()).isEqualTo(160);
                    image.flush();
                }
                return null;
            }));
            start.countDown();
            for (java.util.concurrent.Future<?> task : tasks) task.get(30, java.util.concurrent.TimeUnit.SECONDS);
            try (Stream<Path> files = Files.list(cache.toPath().getParent())) {
                assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("scan_1.png");
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private Path createIncomingPdf(org.apache.pdfbox.pdmodel.common.PDRectangle firstSize) throws Exception {
        Path incoming = Files.createDirectories(tempDir.resolve("incoming"));
        Path directory = Files.createDirectories(incoming.resolve("1/Fax"));
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incoming.toString());
        Path file = directory.resolve("scan.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage(firstSize));
            org.apache.pdfbox.pdmodel.PDPage second = new org.apache.pdfbox.pdmodel.PDPage(
                    new org.apache.pdfbox.pdmodel.common.PDRectangle(240, 320));
            pdf.addPage(second);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream content = new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, second)) {
                content.beginText();
                content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(20, 200);
                content.showText("Second page");
                content.endText();
            }
            // Exercise resources inherited from the page tree in the extraction path.
            pdf.getPages().getCOSObject().setItem(org.apache.pdfbox.cos.COSName.RESOURCES, second.getResources());
            second.getCOSObject().removeItem(org.apache.pdfbox.cos.COSName.RESOURCES);
            pdf.save(file.toFile());
        }
        return file;
    }

    @ParameterizedTest
    @ValueSource(strings = {"viewIncomingDocPageAsPdf", "viewIncomingDocPageAsImage", "displayIncomingDocs"})
    void shouldDenyNamedIncomingQueueBeforeAnyFileOrCacheAccess(String method) throws Exception {
        authorizeIncomingPreview("private.pdf");
        request.setParameter("method", method);
        request.setParameter("queueId", "2");
        try (MockedStatic<io.github.carlos_emr.carlos.documentManager.IncomingDocUtil> incoming = Mockito.mockStatic(io.github.carlos_emr.carlos.documentManager.IncomingDocUtil.class)) {
            assertThat(action.execute()).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsByteArray()).isEmpty();
            incoming.verifyNoInteractions();
        }
        verify(securityInfoManager).hasPrivilege(any(), eq("_queue.2"), eq("r"), isNull());
        verifyNoInteractions(documentDao);
    }

    private void authorizeIncomingPreview(String filename) {
        authorizeEdocWrite();
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        request.setParameter("method", "viewIncomingDocPageAsImage");
        request.setParameter("queueId", "1");
        request.setParameter("pdfDir", "Fax");
        request.setParameter("pdfName", filename);
        request.setParameter("curPage", "1");
    }

    private CtlDocument patientLink(int patient) {
        CtlDocument link = new CtlDocument();
        link.setId(new CtlDocumentPK("demographic", patient, 42));
        return link;
    }

    private MockedStatic<PathValidationUtils> cachePaths() {
        MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class);
        // This unit fixture has no startup properties; the action may already have
        // captured a null static DOCUMENT_DIR. Resolve that test-only configuration
        // to the isolated directory without weakening the production validator.
        paths.when(() -> PathValidationUtils.resolveConfiguredDirectory(nullable(String.class), eq("DOCUMENT_DIR")))
                .thenReturn(tempDir.toFile());
        paths.when(() -> PathValidationUtils.resolveConfiguredDirectory(anyString(), eq("DOCUMENT_CACHE_DIR")))
                .thenReturn(tempDir.toFile());
        paths.when(() -> PathValidationUtils.validateUserFilePath(anyString(), any(File.class)))
                .thenReturn(tempDir.toFile());
        paths.when(() -> PathValidationUtils.validateGeneratedFileName(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        paths.when(() -> PathValidationUtils.validateGeneratedChildPath(anyString(), any(File.class)))
                .thenAnswer(invocation -> tempDir.resolve((String) invocation.getArgument(0)).toFile());
        return paths;
    }

    @Test
    void shouldSanitizeFilename_whenBuildingContentDispositionHeader() throws Exception {
        Method sanitize = ManageDocument2Action.class.getDeclaredMethod("sanitizeHeaderValue", String.class);
        sanitize.setAccessible(true);

        String sanitized = (String) sanitize.invoke(action, "chart\r\nContent-Length: 0.pdf");

        assertThat(sanitized).isEqualTo("chartContent-Length: 0.pdf");
    }

    @Test
    void shouldPropagateFailure_whenProviderRoutingDenied() throws Exception {
        doThrow(new SecurityException("missing _edoc")).when(providerInboxRoutingDao)
                .addToProviderInbox("999998", 42, LabResultData.DOCUMENT);
        Method route = ManageDocument2Action.class.getDeclaredMethod(
                "routeDocumentToProviders", String[].class, String.class);
        route.setAccessible(true);

        assertThatThrownBy(() -> route.invoke(action, new String[] { "999998" }, "42"))
                .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                .hasCauseInstanceOf(SecurityException.class);

        verify(providerInboxRoutingDao).addToProviderInbox("999998", 42, LabResultData.DOCUMENT);
    }

    @Test
    @DisplayName("Moves the exact incoming source file when the queue filename has safe special characters")
    void shouldMoveIncomingDocumentUsingExactSourceFilename_whenFilenameContainsQueueSafeSpecialCharacters() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        String sourceName = "Fax (A+B) R\u00e9sum\u00e9.pdf";
        Path sourceFile = createIncomingSource(incomingDir, sourceName, "original-content");
        Path sanitizedSibling = createIncomingSource(incomingDir, "FaxABRsum.pdf", "wrong-content");
        setupSuccessfulAddIncomingRequest(sourceName);

        String result = runAddIncomingDocumentWithEdocMock();

        assertThat(result).isEqualTo("nextIncomingDoc");
        assertThat(sourceFile).doesNotExist();
        assertThat(sanitizedSibling).exists();
        List<Path> storedFiles = listStoredDocuments();
        assertThat(storedFiles).hasSize(1);
        assertThat(Files.readString(storedFiles.get(0))).isEqualTo("original-content");
    }

    @Test
    @DisplayName("Rejects non-PDF incoming documents before moving them")
    void shouldRejectIncomingDocument_whenSourceIsNotPdf() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "note.txt", "plain-text-content");
        setupSuccessfulAddIncomingRequest("note.txt");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("must be a PDF");
        assertThat(sourceFile).exists();
        assertThat(action.pageCountRequests).isZero();
        assertThat(listStoredDocuments()).isEmpty();
    }

    @Test
    @DisplayName("Rejects GET before adding an incoming document")
    void shouldRejectGet_whenAddingIncomingDocument() throws Exception {
        request.setMethod("GET");

        String result = action.addIncomingDocument();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("Does not overwrite an existing destination when moving an incoming document")
    void shouldNotOverwriteExistingDestination_whenMovingIncomingDocument() throws Exception {
        Path source = Files.writeString(tempDir.resolve("source.pdf"), "source-content");
        Path destination = Files.writeString(tempDir.resolve("destination.pdf"), "existing-content");

        assertThatThrownBy(() -> action.moveIncomingDocument(source.toFile(), destination.toFile()))
                .isInstanceOf(FileAlreadyExistsException.class);

        assertThat(Files.readString(destination)).isEqualTo("existing-content");
        assertThat(source).exists();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nested/report.pdf", "nested\\report.pdf", "C:foo.pdf"})
    @DisplayName("Rejects incoming source filenames with path components")
    void shouldRejectIncomingDocumentSourceFilenameWithPathComponents(String pdfName) throws Exception {
        configureIncomingDocumentDirectories();
        setupSuccessfulAddIncomingRequest(pdfName);

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    @DisplayName("Rejects missing incoming source filename parameters")
    void shouldThrowIllegalArgumentException_whenIncomingDocumentSourceFilenameMissing() throws Exception {
        configureIncomingDocumentDirectories();
        setupSuccessfulAddIncomingRequest("plain.pdf");
        request.removeParameter("pdfName");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid parameters");
    }

    @ParameterizedTest
    @CsvSource({
            "queue1/evil,Fax",
            "1,Fax/evil",
            "../queue1,Fax",
            "1,..\\Fax"
    })
    @DisplayName("Rejects incoming document directory parameters with path components")
    void shouldRejectIncomingDocumentDirectoryParameters_whenPathComponentsProvided(String queueId, String pdfDir) throws Exception {
        configureIncomingDocumentDirectories();
        setupSuccessfulAddIncomingRequest("plain.pdf");
        request.setParameter("queueId", queueId);
        request.setParameter("pdfDir", pdfDir);

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid directory parameters");
    }

    @Test
    @DisplayName("Rejects empty incoming source filenames")
    void shouldRejectIncomingDocumentSourceFilenameEmpty() throws Exception {
        configureIncomingDocumentDirectories();
        setupSuccessfulAddIncomingRequest(" ");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    @DisplayName("Rejects missing incoming source files")
    void shouldReturnKnownUnaccepted_whenIncomingDocumentSourceFileIsMissing() throws Exception {
        configureIncomingDocumentDirectories();
        setupSuccessfulAddIncomingRequest("missing.pdf");
        request.addHeader("X-Carlos-Incoming-Filing", "bounded-v1");
        assertThat(action.addIncomingDocument()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).contains("\"accepted\":false", "\"retryable\":false");
        assertThat(action.pageCountRequests).isZero();
    }

    @Test
    @DisplayName("Rejects incoming source directories")
    void shouldThrowSecurityException_whenIncomingDocumentSourceIsDirectory() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Files.createDirectories(incomingDir.resolve("directory.pdf"));
        setupSuccessfulAddIncomingRequest("directory.pdf");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("regular file");
    }

    @Test
    @DisplayName("Rejects incoming destination filenames that sanitize to hidden files")
    void shouldRejectIncomingDestinationFilename_whenSanitizedNameIsHidden() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        createIncomingSource(incomingDir, ".hidden.pdf", "source-content");
        setupSuccessfulAddIncomingRequest(".hidden.pdf");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    @DisplayName("Rejects incoming destination filenames that sanitize to empty")
    void shouldRejectIncomingDestinationFilename_whenSanitizedNameIsEmpty() {
        assertThatThrownBy(() -> sanitizeIncomingDocumentDestinationFileName("!!!"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    @DisplayName("Rejects incoming source symlinks that escape the incoming directory")
    void shouldThrowSecurityException_whenIncomingDocumentSourceEscapesIncomingDirectoryViaSymlink() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path outsideDir = Files.createTempDirectory(tempDir, "incoming-outside-");
        Path outsideFile = Files.writeString(outsideDir.resolve("victim.pdf"), "victim-content");
        Path symlink = incomingDir.resolve("link.pdf");
        try {
            Files.createSymbolicLink(symlink, outsideFile);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available in this test environment: " + e.getMessage());
        }
        setupSuccessfulAddIncomingRequest("link.pdf");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(SecurityException.class);
        assertThat(outsideFile).exists();
        Files.deleteIfExists(symlink);
        Files.deleteIfExists(outsideFile);
        Files.deleteIfExists(outsideDir);
    }

    @Test
    @DisplayName("Returns an action error and preserves the exact source when the incoming move fails")
    void shouldReturnErrorAndPreserveSource_whenIncomingDocumentMoveFails() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "move-fails.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("move-fails.pdf");
        action.failMove = true;

        String result = runAddIncomingDocumentWithEdocMock();

        assertThat(result).isEqualTo("error");
        assertThat(action.getActionErrors()).contains("Failed to save document file. Please try again or contact your system administrator.");
        assertThat(sourceFile).exists();
        assertThat(Files.readString(sourceFile)).isEqualTo("source-content");
        assertThat(listStoredDocuments()).isEmpty();
    }

    @Test
    @DisplayName("Rejects missing incoming document directory configuration")
    void shouldThrowIllegalStateException_whenIncomingDocumentDirectoryIsMissing() throws Exception {
        Path documentDir = tempDir.resolve("documents");
        Files.createDirectories(documentDir);
        CarlosProperties.getInstance().remove("INCOMINGDOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
        setupSuccessfulAddIncomingRequest("plain.pdf");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INCOMINGDOCUMENT_DIR");
    }

    @Test
    @DisplayName("Rejects empty document directory configuration")
    void shouldThrowIllegalStateException_whenDocumentDirectoryIsEmpty() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", " ");
        createIncomingSource(incomingDir, "plain.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("plain.pdf");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMENT_DIR not configured");
    }

    @Test
    @DisplayName("Rejects document directory configuration that points to a regular file")
    void shouldThrowIllegalStateException_whenConfiguredDocumentDirectoryIsARegularFile() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path regularFile = Files.writeString(tempDir.resolve("document-dir-file"), "not-a-directory");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", regularFile.toString());
        createIncomingSource(incomingDir, "plain.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("plain.pdf");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMENT_DIR is not a directory");
    }

    @Test
    @DisplayName("Capacity refusal occurs before moving source or filing any document")
    void shouldPreserveSourceAndRefuseBeforeAcceptance_whenCapacityBusy() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "busy.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("busy.pdf");
        request.addHeader("X-Carlos-Incoming-Filing", "bounded-v1");
        action.capacityBusy = true;
        try (MockedStatic<EDocUtil> edocUtil = Mockito.mockStatic(EDocUtil.class, Mockito.CALLS_REAL_METHODS)) {
            assertThat(action.addIncomingDocument()).isEqualTo("none");
            edocUtil.verify(() -> EDocUtil.addDocumentSQL(Mockito.any(EDoc.class)), Mockito.never());
        }
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(payload.path("accepted").asBoolean(true)).isFalse();
        assertThat(payload.path("retryable").asBoolean()).isTrue();
        assertThat(payload.path("success").asBoolean(true)).isFalse();
        assertThat(Files.readString(sourceFile)).isEqualTo("source-content");
        assertThat(listStoredDocuments()).isEmpty();
        assertThat(action.pageCountRequests).isEqualTo(1);
        Mockito.verifyNoInteractions(providerInboxRoutingDao);
    }

    @Test
    @DisplayName("Contract callers receive a confirmed document ID and read-only next URL after filing")
    void shouldConfirmContractSuccessAfterFiling() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "accepted.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("accepted.pdf");
        request.addHeader("X-Carlos-Incoming-Filing", "bounded-v1");
        assertThat(runAddIncomingDocumentWithEdocMock()).isEqualTo("none");
        assertThat(sourceFile).doesNotExist();
        assertThat(listStoredDocuments()).hasSize(1);
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(payload.path("success").asBoolean()).isTrue();
        assertThat(payload.path("accepted").asBoolean()).isTrue();
        assertThat(payload.path("documentNo").asInt()).isEqualTo(42);
        assertThat(payload.path("nextUrl").asText()).contains("/documentManager/ViewIncomingDocs?")
                .doesNotContain("addIncomingDocument", "pdfAction", "accepted.pdf");
    }

    @Test
    @DisplayName("Page-count execution failure refuses filing before moving source and is not auto-retryable")
    void shouldPreserveSourceOnPageCountExecutionFailure() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "timeout.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("timeout.pdf");
        request.addHeader("X-Carlos-Incoming-Filing", "bounded-v1");
        action.pageCountFailure = true;
        try (MockedStatic<EDocUtil> edocUtil = Mockito.mockStatic(EDocUtil.class, Mockito.CALLS_REAL_METHODS)) {
            assertThat(action.addIncomingDocument()).isEqualTo("none");
            edocUtil.verify(() -> EDocUtil.addDocumentSQL(Mockito.any(EDoc.class)), Mockito.never());
        }
        assertThat(response.getStatus()).isEqualTo(422);
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(payload.path("accepted").asBoolean(true)).isFalse();
        assertThat(payload.path("retryable").asBoolean(true)).isFalse();
        assertThat(Files.readString(sourceFile)).isEqualTo("source-content");
        assertThat(listStoredDocuments()).isEmpty();
    }

    @Test
    @DisplayName("Post-filing routing/link failure is visible and never invites automatic resubmission")
    void shouldReportUnconfirmedFollowupAfterFiling() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "followup.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("followup.pdf");
        request.addHeader("X-Carlos-Incoming-Filing", "bounded-v1");
        when(ctlDocumentDao.getCtrlDocument(42)).thenThrow(new IllegalStateException("private failure"));
        assertThat(runAddIncomingDocumentWithEdocMock()).isEqualTo("none");
        assertThat(sourceFile).doesNotExist();
        assertThat(listStoredDocuments()).hasSize(1);
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(payload.path("accepted").asBoolean()).isTrue();
        assertThat(payload.path("success").asBoolean(true)).isFalse();
        assertThat(payload.path("retryable").asBoolean(true)).isFalse();
        assertThat(response.getContentAsString()).doesNotContain("private failure");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,false", "false,false", "true,true", "false,true"})
    void shouldReportUnconfirmedWhenPersistenceThrowsAfterMove(boolean contract, boolean revisionFailure) throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "persistence.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("persistence.pdf");
        if (contract) request.addHeader("X-Carlos-Incoming-Filing", "bounded-v1");
        try (MockedStatic<EDocUtil> edocUtil = Mockito.mockStatic(EDocUtil.class, Mockito.CALLS_REAL_METHODS)) {
            edocUtil.when(() -> EDocUtil.addDocumentSQL(Mockito.any(EDoc.class)))
                    .thenThrow(revisionFailure
                            ? new io.github.carlos_emr.carlos.documentManager.StoredDocumentRevision.ConflictException()
                            : new IllegalStateException("private persistence failure"));
            assertThat(action.addIncomingDocument()).isEqualTo("none");
        }
        assertThat(sourceFile).doesNotExist();
        assertThat(listStoredDocuments()).hasSize(1);
        assertThat(Files.readString(listStoredDocuments().get(0))).isEqualTo("source-content");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("private persistence failure", "<form", "Retry saving");
        if (contract) assertThat(response.getContentAsString()).contains("\"accepted\":true", "\"success\":false", "\"retryable\":false");
        else assertThat(response.getContentAsString()).contains("could not be confirmed");
        verifyNoInteractions(patientLabRoutingDao, ctlDocumentDao);
    }

    @Test
    void shouldReportUnconfirmedWhenSelectedProviderRoutingFails() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "routing.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("routing.pdf");
        request.addHeader("X-Carlos-Incoming-Filing", "bounded-v1");
        request.setParameter("flagproviders", "999998");
        doThrow(new SecurityException("private denial")).when(providerInboxRoutingDao)
                .addToProviderInbox("999998", 42, LabResultData.DOCUMENT);
        assertThat(runAddIncomingDocumentWithEdocMock()).isEqualTo("none");
        assertThat(sourceFile).doesNotExist();
        assertThat(listStoredDocuments()).hasSize(1);
        assertThat(response.getContentAsString()).contains("\"accepted\":true", "\"success\":false", "\"retryable\":false")
                .doesNotContain("private denial");
        verifyNoInteractions(patientLabRoutingDao, ctlDocumentDao);
    }

    @Test
    @DisplayName("Invalid, missing or denied patient stops before page parsing or a source move")
    void shouldRefuseInvalidOrInaccessibleIncomingPatients() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "patient-gate.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("patient-gate.pdf");
        for (String invalid : new String[] {"0", "-1", "2147483648", "100 OR 1=1"}) {
            request.setParameter("demog", invalid);
            assertThatThrownBy(() -> action.addIncomingDocument()).isInstanceOf(IllegalArgumentException.class);
        }
        request.setParameter("demog", "100");
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(100))).thenReturn(false);
        assertThatThrownBy(() -> action.addIncomingDocument()).isInstanceOf(SecurityException.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), eq("100"))).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(100))).thenReturn(true);
        when(incomingDemographicDao.getDemographic("100")).thenReturn(null);
        assertThatThrownBy(() -> action.addIncomingDocument()).isInstanceOf(IllegalArgumentException.class);
        assertThat(action.pageCountRequests).isZero();
        assertThat(Files.readString(sourceFile)).isEqualTo("source-content");
        assertThat(listStoredDocuments()).isEmpty();
        Mockito.verifyNoInteractions(providerInboxRoutingDao);
    }

    @Test
    @DisplayName("Patient access revoked during admission refuses the later move")
    void shouldRevalidatePatientAccessAfterCounting() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "revoked.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("revoked.pdf");
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(100))).thenReturn(true, false);
        assertThatThrownBy(() -> action.addIncomingDocument()).isInstanceOf(SecurityException.class);
        assertThat(action.pageCountRequests).isEqualTo(1);
        assertThat(Files.readString(sourceFile)).isEqualTo("source-content");
        assertThat(listStoredDocuments()).isEmpty();
    }

    private Path configureIncomingDocumentDirectories() throws IOException {
        Path incomingRoot = tempDir.resolve("incoming");
        Path documentRoot = tempDir.resolve("documents");
        Files.createDirectories(incomingRoot);
        Files.createDirectories(documentRoot);
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incomingRoot.toString());
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentRoot.toString());
        return Files.createDirectories(incomingRoot.resolve("1").resolve("Fax"));
    }

    @Test
    void shouldDenyPatientSpecificWriteBeforeInspectingQueueOrSource() throws Exception {
        setupSuccessfulAddIncomingRequest("missing.pdf");
        request.setParameter("queueId", "../../invalid");
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), eq("100"))).thenReturn(false);
        assertThatThrownBy(() -> action.addIncomingDocument()).isInstanceOf(SecurityException.class)
                .hasMessageContaining("patient's records");
        assertThat(action.pageCountRequests).isZero();
        verifyNoInteractions(incomingDemographicDao, patientLabRoutingDao, ctlDocumentDao);
    }

    @Test
    void shouldDenyRestrictedQueueBeforeParsingOrMovingSource() throws Exception {
        setupSuccessfulAddIncomingRequest("missing.pdf");
        request.setParameter("queueId", "2");
        assertThatThrownBy(() -> action.addIncomingDocument()).isInstanceOf(SecurityException.class)
                .hasMessageContaining("document queue");
        assertThat(action.pageCountRequests).isZero();
        verifyNoInteractions(patientLabRoutingDao, ctlDocumentDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "changed", "malformed"})
    void shouldRefuseUnobservedIncomingVersionBeforeParsingOrFiling(String reason) throws Exception {
        Path directory = configureIncomingDocumentDirectories();
        Path source = createIncomingSource(directory, "revision.pdf", "original-content");
        setupSuccessfulAddIncomingRequest("revision.pdf");
        request.addHeader("X-Carlos-Incoming-Filing", "bounded-v1");
        if ("missing".equals(reason)) request.removeParameter("sourceRevision");
        if ("duplicate".equals(reason)) request.addParameter("sourceRevision", request.getParameter("sourceRevision"));
        if ("malformed".equals(reason)) request.setParameter("sourceRevision", "A".repeat(64));
        if ("changed".equals(reason)) Files.writeString(source, "changed-content");
        byte[] before = Files.readAllBytes(source);
        assertThat(action.addIncomingDocument()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("\"accepted\":false", "\"sourceChanged\":true", "\"retryable\":false");
        assertThat(action.pageCountRequests).isZero();
        assertThat(Files.readAllBytes(source)).isEqualTo(before);
        assertThat(listStoredDocuments()).isEmpty();
        verifyNoInteractions(patientLabRoutingDao, ctlDocumentDao);
    }

    private Path createIncomingSource(Path incomingDir, String fileName, String content) throws IOException {
        return Files.writeString(incomingDir.resolve(fileName), content);
    }

    private void setupSuccessfulAddIncomingRequest(String pdfName) throws IOException {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "999998");
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("queueId", "1");
        request.setParameter("pdfDir", "Fax");
        request.setParameter("pdfName", pdfName);
        String configuredIncoming = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (configuredIncoming != null && !pdfName.contains("/") && !pdfName.contains("\\")) {
            Path observedSource = Path.of(configuredIncoming).resolve("1/Fax").resolve(pdfName);
            if (Files.isSymbolicLink(observedSource)) {
                // The negative path-containment case must reach the production security gate;
                // never read an external target merely to prepare its submitted revision.
                request.setParameter("sourceRevision", "0".repeat(64));
            } else if (Files.isRegularFile(observedSource, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                request.setParameter("sourceRevision",
                        io.github.carlos_emr.carlos.documentManager.StoredDocumentRevision.sha256(observedSource));
            }
        }
        request.setParameter("demog", "100");
        request.setParameter("observationDate", "2026-05-26");
        request.setParameter("documentDescription", "Incoming document");
        request.setParameter("docType", "DOC");
        request.setParameter("docClass", "class");
        request.setParameter("docSubClass", "subclass");
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), eq("100"))).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(100))).thenReturn(true);
        when(incomingDemographicDao.getDemographic("100")).thenReturn(new io.github.carlos_emr.carlos.commn.model.Demographic());
        when(programManager.getCurrentProgramInDomain(any(), anyString())).thenReturn(null);
        when(patientLabRoutingDao.findByLabNoAndLabType(anyInt(), anyString())).thenReturn(Collections.emptyList());
        when(ctlDocumentDao.getCtrlDocument(42)).thenReturn(nonDemographicCtlDocument());
    }

    private void authorizeEdocWrite() {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), isNull())).thenReturn(true);
    }

    private String runAddIncomingDocumentWithEdocMock() throws Exception {
        try (MockedStatic<EDocUtil> edocUtil = Mockito.mockStatic(EDocUtil.class, Mockito.CALLS_REAL_METHODS)) {
            edocUtil.when(() -> EDocUtil.addDocumentSQL(Mockito.any(EDoc.class))).thenReturn("42");
            return action.addIncomingDocument();
        }
    }

    private CtlDocument nonDemographicCtlDocument() {
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(new CtlDocumentPK("lab", 100, 42));
        return ctlDocument;
    }

    private List<Path> listStoredDocuments() throws IOException {
        try (Stream<Path> stream = Files.list(Path.of(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR")))) {
            return stream.toList();
        }
    }

    private String sanitizeIncomingDocumentDestinationFileName(String fileName) throws Exception {
        Method sanitize = ManageDocument2Action.class.getDeclaredMethod("sanitizeIncomingDocumentDestinationFileName", String.class);
        sanitize.setAccessible(true);
        try {
            return (String) sanitize.invoke(action, fileName);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, previousValue);
        }
    }

    private static final class TestManageDocument2Action extends ManageDocument2Action {
        private boolean failMove;
        private boolean capacityBusy;
        private boolean pageCountFailure;
        private int pageCountRequests;

        @Override
        protected int countIncomingDocumentPages(File source) throws IOException {
            pageCountRequests++;
            assertThat(source).exists();
            if (capacityBusy) throw new io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask.BusyException();
            if (pageCountFailure) throw new IOException("synthetic execution deadline");
            return 1;
        }

        @Override
        protected boolean moveIncomingDocument(File sourceFile, File destFile) throws FileAlreadyExistsException {
            return !failMove && super.moveIncomingDocument(sourceFile, destFile);
        }
    }

    private static final class FailingBinaryResponse extends MockHttpServletResponse {
        private boolean binarySelected;

        @Override
        public jakarta.servlet.ServletOutputStream getOutputStream() {
            binarySelected = true;
            return new jakarta.servlet.ServletOutputStream() {
                @Override
                public void write(int value) throws IOException {
                    throw new IOException("simulated stream failure before commit");
                }

                @Override
                public boolean isReady() { return true; }

                @Override
                public void setWriteListener(jakarta.servlet.WriteListener listener) { }
            };
        }

        @Override
        public PrintWriter getWriter() throws java.io.UnsupportedEncodingException {
            if (binarySelected) throw new IllegalStateException("binary channel already selected");
            return super.getWriter();
        }

        @Override
        public void reset() {
            super.reset();
            binarySelected = false;
        }
    }

    private static final class CommittedFailingResponse extends MockHttpServletResponse {
        @Override
        public boolean isCommitted() {
            return true;
        }

        @Override
        public PrintWriter getWriter() {
            throw new IllegalStateException("writer failed after commit");
        }

        @Override
        public void sendError(int status) {
            throw new AssertionError("sendError must not be called for committed responses");
        }

        @Override
        public void sendError(int status, String errorMessage) {
            throw new AssertionError("sendError must not be called for committed responses");
        }
    }
}
