package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private LoggedInInfo loggedInInfo;

    private DocumentManagerImpl manager;
    private String previousDocumentDir;

    @BeforeEach
    void setUp() {
        manager = new DocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "documentDao", documentDao);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", "")).thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("123");

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
}
