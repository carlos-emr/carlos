/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.StoredDocumentRevision;
import io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class SplitDocument2ActionUnitTest extends CarlosUnitTestBase {
    @TempDir Path directory;
    final DocumentDao documents = mock(DocumentDao.class);
    final CtlDocumentDao links = mock(CtlDocumentDao.class);
    final PatientLabRoutingDao patients = mock(PatientLabRoutingDao.class);
    final ProviderInboxRoutingDao inbox = mock(ProviderInboxRoutingDao.class);
    final ProviderLabRoutingDao providers = mock(ProviderLabRoutingDao.class);
    final QueueDocumentLinkDao queues = mock(QueueDocumentLinkDao.class);
    // The develop-side archive guard (#3484) looks this up when the action is built; an
    // unstubbed mock reports no document as an archive artifact.
    final OutboundEmailArchiveDao archives = mock(OutboundEmailArchiveDao.class);
    final SecurityInfoManager security = mock(SecurityInfoManager.class);
    final ProgramManager2 programs = mock(ProgramManager2.class);
    final LoggedInInfo info = mock(LoggedInInfo.class);
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final TestTransactionManager transactions = new TestTransactionManager();
    MockedStatic<ServletActionContext> servlet;
    MockedStatic<LoggedInInfo> sessions;
    MockedStatic<CarlosProperties> configuration;
    MockedStatic<EDocUtil> edocs;
    TestAction action;
    Document document;
    CarlosProperties properties;
    AtomicReference<EDoc> created = new AtomicReference<>();

    @BeforeEach void setUpAction() throws Exception {
        registerMock(SecurityInfoManager.class, security);
        registerMock(ProgramManager2.class, programs);
        registerMock(DocumentDao.class, documents);
        registerMock(CtlDocumentDao.class, links);
        registerMock(PatientLabRoutingDao.class, patients);
        registerMock(ProviderInboxRoutingDao.class, inbox);
        registerMock(ProviderLabRoutingDao.class, providers);
        registerMock(QueueDocumentLinkDao.class, queues);
        registerMock(OutboundEmailArchiveDao.class, archives);
        registerMock(PlatformTransactionManager.class, transactions);
        request.setMethod("POST"); request.setParameter("document", "42"); request.setParameter("page", "1,0");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        when(info.getLoggedInProviderNo()).thenReturn("999001");
        when(security.hasPrivilege(eq(info), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(info), anyInt())).thenReturn(true);
        properties = mock(CarlosProperties.class);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(directory.toString());
        // Exercise the actual positive-value/default-false property semantics
        // without changing the shared singleton; the static mock closes below.
        when(properties.getProperty("ALLOW_UPDATE_DOCUMENT_CONTENT", "")).thenReturn("");
        when(properties.getBooleanProperty("ALLOW_UPDATE_DOCUMENT_CONTENT", "true")).thenCallRealMethod();
        when(properties.isPropertyActive("ALLOW_UPDATE_DOCUMENT_CONTENT")).thenCallRealMethod();
        configuration = mockStatic(CarlosProperties.class);
        configuration.when(CarlosProperties::getInstance).thenReturn(properties);
        edocs = mockStatic(EDocUtil.class);
        edocs.when(() -> EDocUtil.addDocumentSQL(any())).thenAnswer(call -> { created.set(call.getArgument(0)); return "77"; });
        document = new Document(42); document.setDocfilename("source.pdf"); document.setDoccreator("999001"); document.setStatus('A');
        document.setRestrictToProgram(false);
        when(documents.find(42)).thenReturn(document);
        when(documents.getDocument("42")).thenReturn(document);
        when(documents.findForPageMutation(42)).thenReturn(document);
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage()); pdf.addPage(new PDPage()); pdf.save(directory.resolve("source.pdf").toFile());
        }
        request.setParameter("sourceRevision", StoredDocumentRevision.sha256(directory.resolve("source.pdf")));
        action = new TestAction();
    }

    @AfterEach void tearDownAction() {
        if (edocs != null) edocs.close();
        if (configuration != null) configuration.close();
        if (sessions != null) sessions.close();
        if (servlet != null) servlet.close();
    }

    JsonNode result() throws Exception { return new ObjectMapper().readTree(response.getContentAsString()); }

    @ParameterizedTest @ValueSource(strings = {"traversal", "directory-symlink", "leaf-symlink"})
    void escapingStoredFilenameIsRejectedBeforePreparationOrPersistence(String kind) throws Exception {
        Path store = Files.createDirectory(directory.resolve("store"));
        Path outside = directory.resolve("source.pdf");
        byte[] original = Files.readAllBytes(outside);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(store.toString());
        switch (kind) {
            case "traversal" -> document.setDocfilename("../source.pdf");
            case "directory-symlink" -> {
                Files.createSymbolicLink(store.resolve("linked"), directory);
                document.setDocfilename("linked/source.pdf");
            }
            case "leaf-symlink" -> {
                Files.createSymbolicLink(store.resolve("linked.pdf"), outside);
                document.setDocfilename("linked.pdf");
            }
            default -> throw new IllegalArgumentException();
        }
        action.split();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(result().path("accepted").asBoolean()).isFalse();
        assertThat(result().path("retryable").asBoolean()).isFalse();
        assertThat(action.preparations).isZero();
        assertThat(Files.readAllBytes(outside)).isEqualTo(original);
        verify(documents, never()).findForPageMutation(anyInt());
        edocs.verify(() -> EDocUtil.addDocumentSQL(any()), never());
        verifyNoInteractions(inbox);
    }

    @Test void containedNestedStoredFilenameStillSplitsSuccessfully() throws Exception {
        Path nested = Files.createDirectory(directory.resolve("historical"));
        Files.move(directory.resolve("source.pdf"), nested.resolve("source.pdf"));
        document.setDocfilename("historical/source.pdf");
        byte[] original = Files.readAllBytes(nested.resolve("source.pdf"));
        action.split();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(result().path("newDocNum").asInt()).isEqualTo(77);
        assertThat(Files.readAllBytes(nested.resolve("source.pdf"))).isEqualTo(original);
    }

    @ParameterizedTest @ValueSource(strings = {"after-admission", "before-publication"})
    void escapingFilenameIntroducedAfterInitialValidationStillCannotPublish(String phase) throws Exception {
        Path store = Files.createDirectory(directory.resolve("store"));
        Path outside = directory.resolve("source.pdf");
        Path source = Files.copy(outside, store.resolve("source.pdf"));
        byte[] original = Files.readAllBytes(source);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(store.toString());
        if ("after-admission".equals(phase)) {
            Document changed = new Document(42);
            changed.setDocfilename("../source.pdf"); changed.setStatus('A');
            when(documents.getDocument("42")).thenReturn(document, changed);
        } else {
            action.afterPreparation = () -> document.setDocfilename("../source.pdf");
        }
        action.split();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(result().path("accepted").asBoolean()).isFalse();
        assertThat(result().path("retryable").asBoolean()).isFalse();
        assertThat(action.preparations).isEqualTo("after-admission".equals(phase) ? 0 : 1);
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(Files.readAllBytes(outside)).isEqualTo(original);
        edocs.verify(() -> EDocUtil.addDocumentSQL(any()), never());
        verifyNoInteractions(inbox);
    }

    @ParameterizedTest @ValueSource(strings = {"split", "rotate90", "rotate180", "removeFirstPage"})
    void directEntryRejectsGetBeforeAnyLookup(String operation) throws Exception {
        request.setMethod("GET");
        invoke(operation);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(result().path("accepted").asBoolean()).isFalse();
        verifyNoInteractions(documents, queues, inbox);
    }

    @ParameterizedTest @ValueSource(strings = {"split", "rotate90", "rotate180", "removeFirstPage"})
    void directEntryEnforcesGlobalWritePermission(String operation) throws Exception {
        when(security.hasPrivilege(info, "_edoc", "w", null)).thenReturn(false);
        invoke(operation);
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(documents);
    }

    @Test void patientWriteDenialPreventsPreparingSharedDocument() throws Exception {
        allowAssignedContentChanges("true");
        CtlDocument link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic", 8, 42));
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link));
        when(security.hasPrivilege(info, "_edoc", "w", "8")).thenReturn(false);
        action.split();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(action.preparations).isZero();
    }

    @Test void sourceQueueCannotBeBypassedBySupplyingSharedDestination() throws Exception {
        QueueDocumentLink link = new QueueDocumentLink(); link.setQueueId(9); link.setStatus("A");
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(link));
        when(security.hasPrivilege(info, "_queue.9", "r", null)).thenReturn(false);
        request.setParameter("queueID", "1");
        action.split();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(action.preparations).isZero();
    }

    @Test void busyIsExplicitlyUnacceptedAndWritesNoRowsOrFiles() throws Exception {
        action.busy = true;
        action.split();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(result().path("accepted").asBoolean()).isFalse();
        assertThat(result().path("retryable").asBoolean()).isTrue();
        edocs.verify(() -> EDocUtil.addDocumentSQL(any()), never());
        try (var files = Files.list(directory)) { assertThat(files.toList()).containsExactly(directory.resolve("source.pdf")); }
    }

    @Test void successIsPositiveJsonEvenWhenAllRoutingListsArePresent() throws Exception {
        allowAssignedContentChanges("true");
        PatientLabRouting patient = new PatientLabRouting(); patient.setDemographicNo(8);
        ProviderLabRoutingModel provider = new ProviderLabRoutingModel(); provider.setProviderNo("999002");
        when(patients.findDocByDemographic(42)).thenReturn(List.of(patient));
        when(providers.getProviderLabRoutingDocuments(42)).thenReturn(List.of(provider));
        ProviderInboxItem first = new ProviderInboxItem(); first.setProviderNo("999002");
        ProviderInboxItem second = new ProviderInboxItem(); second.setProviderNo("999003");
        when(inbox.getProvidersWithRoutingForDocument("DOC", 42)).thenReturn(List.of(first, second));
        request.setParameter("queueID", "");
        action.split();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(result().path("success").asBoolean()).isTrue();
        assertThat(result().path("accepted").asBoolean()).isTrue();
        assertThat(result().path("newDocNum").isIntegralNumber()).isTrue();
        assertThat(result().path("newDocNum").asInt()).isEqualTo(77);
        assertThat(directory.resolve(created.get().getFileName())).exists();
        verify(queues).addActiveQueueDocumentLink(1, 77);
        verify(inbox).addToProviderInboxStrict("999001", 77, "DOC");
        verify(inbox).addToProviderInboxStrict("999002", 77, "DOC");
        verify(inbox).addToProviderInboxStrict("999003", 77, "DOC");
        assertThat(action.routedProvider).isEqualTo("999002");
    }

    @Test void providerPrimaryLinkRemainsProviderScoped() throws Exception {
        document.setProgramId(17); document.setRestrictToProgram(true);
        allowProgram(17L);
        CtlDocument primary = new CtlDocument(); primary.setId(new CtlDocumentPK("provider", 999001, 42)); primary.setStatus("A");
        when(links.getCtrlDocument(42)).thenReturn(primary);
        action.split();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(created.get().getModule()).isEqualTo("provider");
        assertThat(created.get().getModuleId()).isEqualTo("999001");
        assertThat(created.get().getProgramId()).isEqualTo(17);
        assertThat(created.get().isRestrictToProgram()).isTrue();
        verify(security, never()).isAllowedAccessToPatientRecord(info, 999001);
    }

    @Test void splitUsesCurrentLockedClassificationRatherThanPreWaitSnapshot() throws Exception {
        Document refreshed = new Document(42); refreshed.setDocfilename("source.pdf"); refreshed.setStatus('A');
        refreshed.setDoccreator("999001"); refreshed.setProgramId(29); refreshed.setRestrictToProgram(true);
        allowProgram(29L);
        when(documents.findForPageMutation(42)).thenAnswer(call -> {
            // Match the refreshed managed entity seen by the central access gate.
            when(documents.find(42)).thenReturn(refreshed);
            return refreshed;
        });
        action.split();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(created.get().getProgramId()).isEqualTo(29);
        assertThat(created.get().isRestrictToProgram()).isTrue();
    }

    private void allowProgram(long programId) {
        ProgramProvider membership = new ProgramProvider();
        membership.setProgramId(programId);
        when(programs.getProgramDomain(info, "999001")).thenReturn(List.of(membership));
    }

    @Test void malformedPdfNeverCreatesADocumentRow() throws Exception {
        Files.writeString(directory.resolve("source.pdf"), "invalid PDF");
        request.setParameter("sourceRevision", StoredDocumentRevision.sha256(directory.resolve("source.pdf")));
        action.split();
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(result().path("accepted").asBoolean()).isFalse();
        edocs.verify(() -> EDocUtil.addDocumentSQL(any()), never());
        verifyNoInteractions(inbox);
    }

    @Test void routingFailureRollsBackOwnedPublicationAndNeverClaimsSuccess() throws Exception {
        doThrow(new IllegalStateException("routing failed")).when(inbox).addToProviderInboxStrict(anyString(), anyInt(), eq("DOC"));
        action.split();
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(result().path("success").asBoolean()).isFalse();
        assertThat(result().path("retryable").asBoolean()).isFalse();
        assertThat(directory.resolve(created.get().getFileName())).doesNotExist();
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test void uncertainCommitKeepsPublishedFileAndForbidsReplay() throws Exception {
        transactions.failCommit = true;
        action.split();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(result().path("accepted").asBoolean()).isTrue();
        assertThat(result().path("retryable").asBoolean()).isFalse();
        assertThat(directory.resolve(created.get().getFileName())).exists();
    }

    @Test void removeReturnsActualPageCountRatherThanBlindlyDecrementingMetadata() throws Exception {
        document.setNumberofpages(99);
        action.removeFirstPage();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(result().path("pageCount").asInt()).isEqualTo(1);
        assertThat(result().path("document").asInt()).isEqualTo(42);
        verify(documents).updatePageCount(42, 1);
        verify(documents, never()).merge(any());
    }

    @Test void cacheFailureRefusesEditBeforeChangingSourceAndCannotAutoReplay() throws Exception {
        byte[] original = Files.readAllBytes(directory.resolve("source.pdf"));
        action.failCache = true;
        action.rotate90();
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(result().path("accepted").asBoolean()).isFalse();
        assertThat(result().path("retryable").asBoolean()).isFalse();
        assertThat(Files.readAllBytes(directory.resolve("source.pdf"))).isEqualTo(original);
    }

    @ParameterizedTest @ValueSource(strings = {"", "not-a-revision", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void missingOrMalformedObservedRevisionCannotPrepareOrMutate(String revision) throws Exception {
        request.setParameter("sourceRevision", revision);
        action.rotate90();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(result().path("accepted").asBoolean()).isFalse();
        assertThat(result().path("retryable").asBoolean()).isFalse();
        assertThat(result().path("sourceChanged").asBoolean()).isTrue();
        assertThat(result().has("sourceRevision")).isFalse();
        assertThat(action.preparations).isZero();
        verifyNoInteractions(documents, inbox);
    }

    @Test void absentAndAmbiguousRevisionFieldsAreRefused() throws Exception {
        request.removeParameter("sourceRevision");
        action.split();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(action.preparations).isZero();
        MockHttpServletResponse nextResponse = new MockHttpServletResponse();
        servlet.when(ServletActionContext::getResponse).thenReturn(nextResponse);
        request.setParameter("sourceRevision", "a".repeat(64), "b".repeat(64));
        new TestAction().split();
        assertThat(nextResponse.getStatus()).isEqualTo(409);
        verifyNoInteractions(documents, inbox);
    }

    @ParameterizedTest @ValueSource(strings = {"split", "removeFirstPage", "rotate90", "rotate180"})
    void anotherSessionsRemovalInvalidatesPreviouslyObservedSelectionBeforeAnySecondMutation(String operation) throws Exception {
        Path source = directory.resolve("source.pdf");
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage()); pdf.addPage(new PDPage()); pdf.addPage(new PDPage()); pdf.save(source.toFile());
        }
        String observedByBoth = StoredDocumentRevision.sha256(source);
        request.setParameter("sourceRevision", observedByBoth);
        action.removeFirstPage();
        assertThat(response.getStatus()).isEqualTo(200);
        String nextRevision = result().path("sourceRevision").asText();
        assertThat(nextRevision).isEqualTo(StoredDocumentRevision.sha256(source)).isNotEqualTo(observedByBoth);
        byte[] firstCommittedBytes = Files.readAllBytes(source);
        // Still-valid numeric page2 now means a different original page. The
        // second window must get a conflict rather than silently rebasing it.
        request.setParameter("page", "2,0");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        servlet.when(ServletActionContext::getResponse).thenReturn(secondResponse);
        TestAction second = new TestAction();
        switch (operation) {
            case "split" -> second.split(); case "removeFirstPage" -> second.removeFirstPage();
            case "rotate90" -> second.rotate90(); case "rotate180" -> second.rotate180();
            default -> throw new IllegalArgumentException();
        }
        JsonNode conflict = new ObjectMapper().readTree(secondResponse.getContentAsString());
        assertThat(secondResponse.getStatus()).isEqualTo(409);
        assertThat(conflict.path("sourceChanged").asBoolean()).isTrue();
        assertThat(conflict.path("accepted").asBoolean()).isFalse();
        assertThat(conflict.path("retryable").asBoolean()).isFalse();
        assertThat(conflict.has("sourceRevision")).isFalse();
        assertThat(second.preparations).isZero();
        assertThat(Files.readAllBytes(source)).isEqualTo(firstCommittedBytes);
        verify(documents, times(1)).updatePageCount(42, 2);
        edocs.verify(() -> EDocUtil.addDocumentSQL(any()), never());
    }

    @Test void confirmedSuccessRevisionAllowsOnlyAnExplicitNewIntentToEditAgain() throws Exception {
        action.rotate90();
        assertThat(response.getStatus()).isEqualTo(200);
        request.setParameter("sourceRevision", result().path("sourceRevision").asText());
        MockHttpServletResponse nextResponse = new MockHttpServletResponse();
        servlet.when(ServletActionContext::getResponse).thenReturn(nextResponse);
        new TestAction().rotate180();
        assertThat(nextResponse.getStatus()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(nextResponse.getContentAsString()).path("sourceRevision").asText())
                .isEqualTo(StoredDocumentRevision.sha256(directory.resolve("source.pdf")));
        verify(documents, times(2)).updatePageCount(42, 2);
    }

    @Test void replacementOutsideTheLeaseDuringPreparationIsDetectedAgainBeforePublication() throws Exception {
        Path source = directory.resolve("source.pdf");
        action.afterPreparation = () -> {
            try { Files.writeString(source, "external completed replacement"); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        };
        action.rotate90();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(result().path("accepted").asBoolean()).isFalse();
        assertThat(result().path("sourceChanged").asBoolean()).isTrue();
        assertThat(Files.readString(source)).isEqualTo("external completed replacement");
        verify(documents, never()).updatePageCount(anyInt(), anyInt());
        verifyNoInteractions(inbox);
    }

    @ParameterizedTest @ValueSource(strings = {"split", "rotate90", "rotate180", "removeFirstPage"})
    void explicitFalseRejectsAllAssignedMutationsBeforePreparation(String operation) throws Exception {
        allowAssignedContentChanges("false");
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(8)));
        byte[] original = Files.readAllBytes(directory.resolve("source.pdf"));
        invoke(operation);
        assertPolicyRefusal(original, 0);
    }

    @ParameterizedTest @ValueSource(strings = {"split", "rotate90", "rotate180", "removeFirstPage"})
    void missingPropertyRejectsRoutingOnlyAssignedMutations(String operation) throws Exception {
        PatientLabRouting route = new PatientLabRouting();
        route.setLabNo(42); route.setLabType("DOC"); route.setDemographicNo(8);
        when(patients.findDocByDemographic(42)).thenReturn(List.of(route));
        byte[] original = Files.readAllBytes(directory.resolve("source.pdf"));
        invoke(operation);
        assertPolicyRefusal(original, 0);
    }

    @Test void positivePatientAmongMixedLinksCannotHideBehindUnassignedPrimary() throws Exception {
        when(links.getCtrlDocument(42)).thenReturn(patientLink(-1));
        when(links.findByDocumentNoAndModule(42, "demographic"))
                .thenReturn(List.of(patientLink(-1), patientLink(0), patientLink(8), patientLink(9)));
        byte[] original = Files.readAllBytes(directory.resolve("source.pdf"));
        action.split();
        assertPolicyRefusal(original, 0);
    }

    @ParameterizedTest @ValueSource(strings = {"split", "rotate90", "rotate180", "removeFirstPage"})
    void unassignedDocumentRemainsEditableWithDefaultFalse(String operation) throws Exception {
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(-1), patientLink(0)));
        invoke(operation);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(result().path("success").asBoolean()).isTrue();
        assertThat(result().path("accepted").asBoolean()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"split", "rotate90", "rotate180", "removeFirstPage"})
    void explicitTrueAllowsAssignedDocumentWithPatientWriteAccess(String operation) throws Exception {
        allowAssignedContentChanges("true");
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(8)));
        invoke(operation);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(result().path("accepted").asBoolean()).isTrue();
        verify(security, atLeastOnce()).hasPrivilege(info, "_edoc", "w", "8");
    }

    @Test void assignmentObservedAfterLeaseAdmissionRefusesBeforePreparation() throws Exception {
        java.util.concurrent.atomic.AtomicInteger lookups = new java.util.concurrent.atomic.AtomicInteger();
        when(documents.getDocument("42")).thenAnswer(call -> {
            if (lookups.incrementAndGet() == 2) {
                when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(8)));
            }
            return document;
        });
        byte[] original = Files.readAllBytes(directory.resolve("source.pdf"));
        action.rotate90();
        assertPolicyRefusal(original, 0);
        assertThat(lookups.get()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"split", "rotate90", "rotate180", "removeFirstPage"})
    void assignmentAddedDuringPreparationIsRecheckedInTransactionBeforePublication(String operation) throws Exception {
        action.afterPreparation = () -> {
            PatientLabRouting route = new PatientLabRouting();
            route.setLabNo(42); route.setLabType("DOC"); route.setDemographicNo(8);
            when(patients.findDocByDemographic(42)).thenReturn(List.of(route));
        };
        byte[] original = Files.readAllBytes(directory.resolve("source.pdf"));
        invoke(operation);
        assertPolicyRefusal(original, 1);
        verify(documents).findForPageMutation(42);
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test void disablingPolicyDuringPreparationRefusesPreviouslyAllowedAssignedDocument() throws Exception {
        allowAssignedContentChanges("true");
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patientLink(8)));
        action.afterPreparation = () -> allowAssignedContentChanges("false");
        byte[] original = Files.readAllBytes(directory.resolve("source.pdf"));
        action.removeFirstPage();
        assertPolicyRefusal(original, 1);
    }

    private void allowAssignedContentChanges(String value) {
        when(properties.getProperty("ALLOW_UPDATE_DOCUMENT_CONTENT", "")).thenReturn(value);
    }

    private CtlDocument patientLink(int patient) {
        CtlDocument link = new CtlDocument();
        link.setId(new CtlDocumentPK("demographic", patient, 42)); link.setStatus("A");
        return link;
    }

    private void assertPolicyRefusal(byte[] original, int preparations) throws Exception {
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(result().path("success").asBoolean()).isFalse();
        assertThat(result().path("accepted").asBoolean()).isFalse();
        assertThat(result().path("retryable").asBoolean()).isFalse();
        assertThat(response.getHeader("Retry-After")).isNull();
        assertThat(action.preparations).isEqualTo(preparations);
        assertThat(Files.readAllBytes(directory.resolve("source.pdf"))).isEqualTo(original);
        try (var files = Files.list(directory)) { assertThat(files.toList()).containsExactly(directory.resolve("source.pdf")); }
        edocs.verify(() -> EDocUtil.addDocumentSQL(any()), never());
        verify(documents, never()).updatePageCount(anyInt(), anyInt());
        verifyNoInteractions(inbox);
    }

    private void invoke(String operation) throws IOException {
        switch (operation) {
            case "split" -> action.split(); case "rotate90" -> action.rotate90();
            case "rotate180" -> action.rotate180(); case "removeFirstPage" -> action.removeFirstPage();
            default -> throw new IllegalArgumentException();
        }
    }

    static class TestAction extends SplitDocument2Action {
        boolean busy; boolean failCache; int preparations; String routedProvider; Runnable afterPreparation;
        @Override protected SplitDocumentPdfWork.Prepared prepare(Path source, Path directory,
                SplitDocumentPdfWork.Operation operation, List<SplitDocumentPdfWork.PageSelection> selections) throws IOException {
            preparations++;
            if (busy) throw new BoundedPdfTask.BusyException();
            SplitDocumentPdfWork.Prepared prepared = SplitDocumentPdfWork.prepareNow(source, directory, operation, selections);
            if (afterPreparation != null) afterPreparation.run();
            return prepared;
        }
        @Override protected void routeProvider(String number, String provider) { routedProvider = provider; }
        @Override protected void invalidateCaches(Document document, int pages) throws IOException {
            if (failCache) throw new IOException("cache invalidation failed");
        }
    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        boolean failCommit; int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("Unconfirmed commit");
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
