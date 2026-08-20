package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("DocumentManagerImpl filename validation")
@Tag("unit")
@Tag("security")
class DocumentManagerImplFilenameValidationTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    private SecurityInfoManager securityInfoManager;
    private DocumentDao documentDao;
    private CtlDocumentDao ctlDocumentDao;
    private PatientLabRoutingDao patientLabRoutingDao;
    private ProviderLabRoutingDao providerLabRoutingDao;
    private LoggedInInfo loggedInInfo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        documentDao = mock(DocumentDao.class);
        ctlDocumentDao = mock(CtlDocumentDao.class);
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        providerLabRoutingDao = mock(ProviderLabRoutingDao.class);
        loggedInInfo = mock(LoggedInInfo.class);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("w"), eq("")))
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
    @DisplayName("should expose the server-generated filename on the caller's document when persistence fails")
    void shouldExposeServerGeneratedFilename_whenPersistenceFailsAfterFileWrite() {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString());
            doThrow(new IllegalStateException("document insert failed"))
                    .when(documentDao).persist(any(Document.class));

            DocumentManagerImpl manager = newDocumentManager();
            Document document = new Document();
            document.setDocfilename("scan.pdf");

            assertThatThrownBy(() -> manager.createDocument(loggedInInfo, document, null, null,
                    "patient bytes".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("document insert failed");

            // The bytes were already written under a server-generated name. Callers that clean
            // up an orphaned file on failure (OutboundEmailArchiveServiceImpl) can only find it
            // if docfilename was updated on the caller's Document before the failure — the
            // caller's own "scan.pdf" points at nothing on disk.
            assertThat(document.getDocfilename()).matches("\\d{14}_\\d{5}_scan\\.pdf");
            assertThat(Files.exists(tempDir.resolve(document.getDocfilename()))).isTrue();
        }
    }

    @Test
    @DisplayName("should leave no partial file behind when the document write fails part-way")
    void shouldLeaveNoPartialFile_whenDocumentWriteFailsPartWay() {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class);
             MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString());

            // CREATE_NEW creates the file and only then writes it, so a mid-write failure — disk
            // full, quota, IO error — leaves a partial document on disk. Reproduce that shape:
            // create the file the way the real call would, then fail the write.
            AtomicReference<Path> partial = new AtomicReference<>();
            filesMock.when(() -> Files.write(any(Path.class), any(byte[].class), any(OpenOption.class)))
                    .thenAnswer(invocation -> {
                        Path target = invocation.getArgument(0);
                        partial.set(target);
                        target.toFile().createNewFile();
                        throw new IOException("No space left on device");
                    });

            DocumentManagerImpl manager = newDocumentManager();
            Document document = new Document();
            document.setDocfilename("scan.pdf");

            assertThatThrownBy(() -> manager.createDocument(loggedInInfo, document, null, null,
                    "patient bytes".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("No space left on device");

            // No caller can clean this up: docfilename is only assigned once the write succeeds,
            // so the caller's Document still names "scan.pdf" and any cleanup it attempts silently
            // matches nothing. An unreferenced partial document is PHI no database row names.
            assertThat(partial.get()).isNotNull();
            assertThat(partial.get()).doesNotExist();
            assertThat(document.getDocfilename()).isEqualTo("scan.pdf");
        }
    }

    private DocumentManagerImpl newDocumentManager() {
        DocumentManagerImpl manager = new DocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "documentDao", documentDao);
        injectDependency(manager, "ctlDocumentDao", ctlDocumentDao);
        injectDependency(manager, "patientLabRoutingDao", patientLabRoutingDao);
        injectDependency(manager, "providerLabRoutingDao", providerLabRoutingDao);
        return manager;
    }
}
