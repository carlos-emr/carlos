package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.documentManager.dto.DocumentListItemDTO;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DocumentManagerImpl filename validation")
@Tag("unit")
@Tag("security")
class DocumentManagerImplFilenameValidationTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    private SecurityInfoManager securityInfoManager;
    private DocumentDao documentDao;
    private CtlDocumentDao ctlDocumentDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private PatientLabRoutingDao patientLabRoutingDao;
    private ProviderInboxRoutingDao providerInboxRoutingDao;
    private ProviderLabRoutingDao providerLabRoutingDao;
    private QueueDocumentLinkDao queueDocumentLinkDao;
    private LoggedInInfo loggedInInfo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        documentDao = mock(DocumentDao.class);
        ctlDocumentDao = mock(CtlDocumentDao.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        providerInboxRoutingDao = mock(ProviderInboxRoutingDao.class);
        providerLabRoutingDao = mock(ProviderLabRoutingDao.class);
        queueDocumentLinkDao = mock(QueueDocumentLinkDao.class);
        loggedInInfo = mock(LoggedInInfo.class);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("w"), eq("")))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("x"), eq("")))
                .thenReturn(true);
        doAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setDocumentNo(321);
            return null;
        }).when(documentDao).persist(any(Document.class));
    }

    @Test
    @DisplayName("should keep timestamp prefix after normalizing path-shaped document filename")
    void shouldKeepTimestampPrefix_afterNormalizingPathShapedDocumentFilename() throws Exception {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString());

            DocumentManagerImpl manager = newDocumentManager();
            Document document = new Document();
            document.setDocfilename("nested/path/my report.txt");

            Document result = manager.createDocument(loggedInInfo, document, null, null,
                    "document body".getBytes(StandardCharsets.UTF_8));

            // Server-generated collision-resistant name: yyyyMMddHHmmss_NNNNN_<validatedName>
            assertThat(result.getDocfilename()).matches("\\d{14}_\\d{5}_my_report\\.txt");
            assertThat(Files.readString(tempDir.resolve(result.getDocfilename()))).isEqualTo("document body");
            assertThat(Files.exists(tempDir.resolve("my_report.txt"))).isFalse();
        }
    }

    @Test
    @DisplayName("should write distinct files when two same-name documents are created back-to-back")
    void shouldWriteDistinctFiles_whenTwoSameNameDocumentsCreatedBackToBack() throws Exception {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString());
            DocumentManagerImpl manager = newDocumentManager();

            Document first = new Document();
            first.setDocfilename("scan.pdf");
            Document second = new Document();
            second.setDocfilename("scan.pdf");

            // Two uploads of the same original name in immediate succession (worst case: same second).
            Document firstResult = manager.createDocument(loggedInInfo, first, null, null,
                    "patient-A".getBytes(StandardCharsets.UTF_8));
            Document secondResult = manager.createDocument(loggedInInfo, second, null, null,
                    "patient-B".getBytes(StandardCharsets.UTF_8));

            // Distinct stored filenames — the atomic sequence prevents the collision...
            assertThat(firstResult.getDocfilename()).isNotEqualTo(secondResult.getDocfilename());
            // ...and neither file was overwritten: each still holds its own patient's bytes.
            assertThat(Files.readString(tempDir.resolve(firstResult.getDocfilename()))).isEqualTo("patient-A");
            assertThat(Files.readString(tempDir.resolve(secondResult.getDocfilename()))).isEqualTo("patient-B");
        }
    }

    @Test
    @DisplayName("should reject generated filenames that belong to outbound archive eDocs")
    void shouldRejectCreateDocument_whenGeneratedFilenameBelongsToOutboundArchive() throws Exception {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString());
            when(outboundEmailArchiveDao.existsByFileName(anyString())).thenReturn(true);

            DocumentManagerImpl manager = newDocumentManager();
            Document document = new Document();
            document.setDocfilename("archive.pdf");

            assertThatThrownBy(() -> manager.createDocument(loggedInInfo, document, null, null,
                    "document body".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("outbound email archive workflow");
            try (Stream<Path> files = Files.list(tempDir)) {
                assertThat(files).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("should reject normal rendering for outbound archive backing eDocs")
    void shouldRejectRenderDocument_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_newCasemgmt.documents"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.renderDocument(loggedInInfo, "321"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject generic document reads for outbound archive backing eDocs")
    void shouldRejectGetDocument_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(321);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("r"), eq("")))
                .thenReturn(true);
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(document);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.getDocument(loggedInInfo, 321))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject control-document reads for outbound archive backing eDocs")
    void shouldRejectGetCtlDocument_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("r"), eq("")))
                .thenReturn(true);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.getCtlDocumentByDocumentId(loggedInInfo, 321))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
        verify(ctlDocumentDao, never()).getCtrlDocument(321);
    }

    @Test
    @DisplayName("should filter outbound archive eDocs from generic document sync lists")
    void shouldFilterOutboundArchiveDocuments_whenListingUpdatedDocuments() {
        DocumentManagerImpl manager = newDocumentManager();
        Document ordinaryDocument = document(654);
        Date since = new Date(0L);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("r"), eq("")))
                .thenReturn(true);
        when(documentDao.findByUpdateDateExcludingOutboundEmailArchives(since, 10))
                .thenReturn(List.of(ordinaryDocument));

        List<Document> documents = manager.getDocumentsUpdateAfterDate(loggedInInfo, since, 10);

        assertThat(documents).containsExactly(ordinaryDocument);
        verify(documentDao).findByUpdateDateExcludingOutboundEmailArchives(since, 10);
    }

    @Test
    @DisplayName("should apply the sync limit after excluding archive rows in the database")
    void shouldApplySyncLimitAfterExcludingArchiveRows() {
        DocumentManagerImpl manager = newDocumentManager();
        Document firstOrdinary = document(654);
        Document secondOrdinary = document(655);
        Date since = new Date(0L);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("r"), eq("")))
                .thenReturn(true);
        when(documentDao.findByUpdateDateExcludingOutboundEmailArchives(since, 2))
                .thenReturn(List.of(firstOrdinary, secondOrdinary));

        List<Document> documents = manager.getDocumentsUpdateAfterDate(loggedInInfo, since, 2);

        assertThat(documents).containsExactly(firstOrdinary, secondOrdinary);
        verify(documentDao).findByUpdateDateExcludingOutboundEmailArchives(since, 2);
        verify(documentDao, never()).findByUpdateDate(since, 2);
    }

    @Test
    @DisplayName("should filter outbound archive eDocs from generic document DTO lists")
    void shouldFilterOutboundArchiveDocuments_whenListingDocumentDtos() {
        DocumentManagerImpl manager = newDocumentManager();
        DocumentListItemDTO archiveDocument = documentDto(321);
        DocumentListItemDTO ordinaryDocument = documentDto(654);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("r"), isNull()))
                .thenReturn(true);
        when(documentDao.findDocumentDTOsByDemographicNo(123)).thenReturn(List.of(archiveDocument, ordinaryDocument));
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321, 654))).thenReturn(Set.of(321));

        List<DocumentListItemDTO> documents = manager.getDocumentDTOs(loggedInInfo, 123);

        assertThat(documents)
                .extracting(DocumentListItemDTO::getDocumentNo)
                .containsExactly(654);
    }

    @Test
    @DisplayName("should reject moving outbound archive backing eDocs")
    void shouldRejectMoveDocument_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(321);
        document.setDocfilename("ordinary.pdf");
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.moveDocument(loggedInInfo, document, tempDir.toString(), tempDir.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject moving documents over outbound archive filenames")
    void shouldRejectMoveDocument_whenFilenameBelongsToOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(654);
        document.setDocfilename("archive.pdf");
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> manager.moveDocument(loggedInInfo, document, tempDir.toString(), tempDir.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject saving outbound archive backing eDocs through generic updates")
    void shouldRejectSaveDocument_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(321);
        document.setDocfilename("ordinary.pdf");
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.saveDocument(loggedInInfo, document, ctlDocument(123)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject saving documents over outbound archive filenames")
    void shouldRejectSaveDocument_whenFilenameBelongsToOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(654);
        document.setDocfilename("archive.pdf");
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> manager.saveDocument(loggedInInfo, document, ctlDocument(123)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject writing documents over outbound archive filenames")
    void shouldRejectAddDocument_whenFilenameBelongsToOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(null);
        document.setDocfilename("archive.pdf");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_newCasemgmt.documents"),
                eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_newCasemgmt.documents"),
                eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> manager.addDocument(loggedInInfo, document, ctlDocument(123)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject queueing outbound archive backing eDocs")
    void shouldRejectAddDocumentToQueue_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.addDocumentToQueue(loggedInInfo, 321, 7))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject acknowledgement-provider reads for outbound archive backing eDocs")
    void shouldRejectAcknowledgedProviders_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.getProvidersThatHaveAcknowledgedDocument(loggedInInfo, 321))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
        verify(providerInboxRoutingDao, never()).getProvidersWithRoutingForDocument(anyString(), eq(321));
    }

    private Document document(Integer documentNo) {
        Document document = new Document();
        document.setDocumentNo(documentNo);
        document.setStatus(Document.STATUS_ACTIVE);
        return document;
    }

    private DocumentListItemDTO documentDto(Integer documentNo) {
        DocumentListItemDTO document = new DocumentListItemDTO();
        document.setDocumentNo(documentNo);
        return document;
    }

    private CtlDocument ctlDocument(Integer demographicNo) {
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(new CtlDocumentPK("demographic", demographicNo, null));
        return ctlDocument;
    }

    private DocumentManagerImpl newDocumentManager() {
        DocumentManagerImpl manager = new DocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "documentDao", documentDao);
        injectDependency(manager, "ctlDocumentDao", ctlDocumentDao);
        injectDependency(manager, "outboundEmailArchiveDao", outboundEmailArchiveDao);
        injectDependency(manager, "patientLabRoutingDao", patientLabRoutingDao);
        injectDependency(manager, "providerInboxRoutingDao", providerInboxRoutingDao);
        injectDependency(manager, "providerLabRoutingDao", providerLabRoutingDao);
        injectDependency(manager, "queueDocumentLinkDAO", queueDocumentLinkDao);
        return manager;
    }
}
