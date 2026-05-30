package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.commn.dao.CtlDocTypeDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.documentManager.EDoc;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("action")
class ManageDocument2ActionTest extends CarlosUnitTestBase {

    @Mock
    private DocumentDao documentDao;

    @Mock
    private CtlDocumentDao ctlDocumentDao;

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

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        previousIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        registerMock(DocumentDao.class, documentDao);
        registerMock(CtlDocumentDao.class, ctlDocumentDao);
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
    void shouldReturnNoneAndSendForbidden_whenShowPageDeniesAuthorization() {
        request.setParameter("method", "showPage");
        request.setParameter("page", "1");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(action.getActionErrors()).isEmpty();
    }

    @Test
    void shouldSanitizeFilename_whenBuildingContentDispositionHeader() throws Exception {
        Method sanitize = ManageDocument2Action.class.getDeclaredMethod("sanitizeHeaderValue", String.class);
        sanitize.setAccessible(true);

        String sanitized = (String) sanitize.invoke(action, "chart\r\nContent-Length: 0.pdf");

        assertThat(sanitized).isEqualTo("chartContent-Length: 0.pdf");
    }

    @Test
    void shouldLogWarning_whenProviderRoutingDenied() throws Exception {
        doThrow(new SecurityException("missing _edoc")).when(providerInboxRoutingDao)
                .addToProviderInbox("999998", 42, LabResultData.DOCUMENT);
        Method route = ManageDocument2Action.class.getDeclaredMethod(
                "routeDocumentToProviders", String[].class, String.class, String.class);
        route.setAccessible(true);

        route.invoke(action, new String[] { "999998" }, "42", "100");

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
    @DisplayName("Moves non-PDF incoming documents without page counting")
    void shouldMoveIncomingDocumentWithoutCountingPages_whenSourceIsNotPdf() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "note.txt", "plain-text-content");
        setupSuccessfulAddIncomingRequest("note.txt");

        String result = runAddIncomingDocumentWithEdocMock();

        assertThat(result).isEqualTo("nextIncomingDoc");
        assertThat(sourceFile).doesNotExist();
        assertThat(action.pageCountRequests).isZero();
        List<Path> storedFiles = listStoredDocuments();
        assertThat(storedFiles).hasSize(1);
        assertThat(Files.readString(storedFiles.get(0))).isEqualTo("plain-text-content");
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
            "queue1,Fax/evil",
            "../queue1,Fax",
            "queue1,..\\Fax"
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
    void shouldThrowSecurityException_whenIncomingDocumentSourceFileIsMissing() throws Exception {
        configureIncomingDocumentDirectories();
        setupSuccessfulAddIncomingRequest("missing.pdf");

        assertThatThrownBy(() -> action.addIncomingDocument())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("regular file");
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
        Path outsideDir = Files.createTempDirectory(Path.of(System.getProperty("user.dir")), "incoming-outside-");
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
    @DisplayName("Returns an action error and deletes the source when the incoming move fails")
    void shouldReturnErrorAndDeleteSource_whenIncomingDocumentMoveFails() throws Exception {
        Path incomingDir = configureIncomingDocumentDirectories();
        Path sourceFile = createIncomingSource(incomingDir, "move-fails.pdf", "source-content");
        setupSuccessfulAddIncomingRequest("move-fails.pdf");
        action.failMove = true;

        String result = runAddIncomingDocumentWithEdocMock();

        assertThat(result).isEqualTo("error");
        assertThat(action.getActionErrors()).contains("Failed to save document file. Please try again or contact your system administrator.");
        assertThat(sourceFile).doesNotExist();
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

    private Path configureIncomingDocumentDirectories() throws IOException {
        Path incomingRoot = tempDir.resolve("incoming");
        Path documentRoot = tempDir.resolve("documents");
        Files.createDirectories(incomingRoot);
        Files.createDirectories(documentRoot);
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incomingRoot.toString());
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentRoot.toString());
        return Files.createDirectories(incomingRoot.resolve("queue1").resolve("Fax"));
    }

    private Path createIncomingSource(Path incomingDir, String fileName, String content) throws IOException {
        return Files.writeString(incomingDir.resolve(fileName), content);
    }

    private void setupSuccessfulAddIncomingRequest(String pdfName) {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "999998");
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("queueId", "queue1");
        request.setParameter("pdfDir", "Fax");
        request.setParameter("pdfName", pdfName);
        request.setParameter("demog", "100");
        request.setParameter("observationDate", "2026-05-26");
        request.setParameter("documentDescription", "Incoming document");
        request.setParameter("docType", "DOC");
        request.setParameter("docClass", "class");
        request.setParameter("docSubClass", "subclass");
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), isNull())).thenReturn(true);
        when(programManager.getCurrentProgramInDomain(any(), anyString())).thenReturn(null);
        when(patientLabRoutingDao.findByLabNoAndLabType(anyInt(), anyString())).thenReturn(Collections.emptyList());
        when(ctlDocumentDao.getCtrlDocument(42)).thenReturn(nonDemographicCtlDocument());
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
        private int pageCountRequests;

        @Override
        public int countNumOfPages(String fileName) {
            pageCountRequests++;
            return 1;
        }

        @Override
        protected boolean moveIncomingDocument(File sourceFile, File destFile) throws FileAlreadyExistsException {
            return !failMove && super.moveIncomingDocument(sourceFile, destFile);
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
