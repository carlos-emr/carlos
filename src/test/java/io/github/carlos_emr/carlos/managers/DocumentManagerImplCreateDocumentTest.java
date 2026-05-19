package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentManagerImpl createDocument")
@Tag("unit")
@Tag("documentManager")
class DocumentManagerImplCreateDocumentTest extends CarlosUnitTestBase {

    @TempDir
    private Path documentDir;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private DocumentDao documentDao;

    @Mock
    private CtlDocumentDao ctlDocumentDao;

    @Mock
    private LoggedInInfo loggedInInfo;

    private DocumentManagerImpl manager;
    private String previousDocumentDir;

    @BeforeEach
    void setUp() {
        manager = new DocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "documentDao", documentDao);
        injectDependency(manager, "ctlDocumentDao", ctlDocumentDao);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", "")).thenReturn(true);
        lenient().when(loggedInInfo.getLoggedInProviderNo()).thenReturn("123");

        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (previousDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        }
    }

    @Test
    @DisplayName("deletes copied file when document persistence fails before record creation")
    void deletesCopiedFile_whenDocumentPersistenceFailsBeforeRecordCreation() throws Exception {
        Document document = new Document();
        document.setDocfilename("failed upload.txt");
        document.setDocdesc("Failed upload");

        doThrow(new RuntimeException("database unavailable"))
                .when(documentDao)
                .persist(any(Document.class));

        assertThatThrownBy(() -> manager.createDocument(loggedInInfo, document, 123, null, new byte[]{1, 2, 3}))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("database unavailable");

        try (var files = Files.list(documentDir)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    @DisplayName("does not create a file when document data is missing")
    void doesNotCreateFile_whenDocumentDataIsMissing() {
        Document document = new Document();
        document.setDocfilename("missing data.txt");
        document.setDocdesc("Missing data");

        assertThatThrownBy(() -> manager.createDocument(loggedInInfo, document, 123, null, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Document data is required");

        try (var files = Files.list(documentDir)) {
            assertThat(files.toList()).isEmpty();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("appends a suffix when the timestamped document filename already exists")
    void appendsSuffix_whenTimestampedDocumentFilenameAlreadyExists() throws Exception {
        Document document = new Document();
        document.setDocfilename("duplicate.txt");
        document.setDocdesc("Duplicate upload");
        createLikelyTimestampCollisions("duplicate.txt");
        doAnswer(invocation -> {
            Document persistedDocument = invocation.getArgument(0);
            persistedDocument.setDocumentNo(42);
            return null;
        }).when(documentDao).persist(any(Document.class));

        Document createdDocument = manager.createDocument(loggedInInfo, document, null, null, new byte[]{9});

        assertThat(createdDocument.getDocfilename()).matches("\\d{14}_duplicate_1\\.txt");
        assertThat(Files.readAllBytes(documentDir.resolve(createdDocument.getDocfilename()))).containsExactly(9);
        String originalCollisionName = createdDocument.getDocfilename().replace("_1.txt", ".txt");
        assertThat(Files.exists(documentDir.resolve(originalCollisionName))).isTrue();
    }

    private void createLikelyTimestampCollisions(String normalizedFileName) throws IOException {
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        long now = System.currentTimeMillis();
        for (int offsetSeconds = -1; offsetSeconds <= 30; offsetSeconds++) {
            String storageFileName = dateTimeFormat.format(new Date(now + offsetSeconds * 1000L))
                    + "_" + normalizedFileName;
            Files.write(documentDir.resolve(storageFileName), new byte[]{0});
        }
    }
}
