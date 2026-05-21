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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("DocumentManagerImpl moveDocument")
@Tag("unit")
@Tag("security")
class DocumentManagerImplMoveDocumentTest extends CarlosUnitTestBase {

    @TempDir
    private Path tempDir;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    private AutoCloseable mocks;
    private DocumentManagerImpl manager;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        manager = new DocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "x", "")).thenReturn(true);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("moves document from validated temp source to destination")
    void movesDocumentFromValidatedTempSourceToDestination() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path destinationDir = Files.createDirectory(tempDir.resolve("destination"));
        String filename = "generated-document.pdf";
        Files.write(sourceDir.resolve(filename), new byte[]{1, 2, 3});

        Document document = new Document();
        document.setDocfilename(filename);
        document.setDocumentNo(42);

        manager.moveDocument(loggedInInfo, document, sourceDir.toString(), destinationDir.toString());

        assertThat(Files.exists(sourceDir.resolve(filename))).isFalse();
        assertThat(Files.readAllBytes(destinationDir.resolve(filename))).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("moves document from configured document store source to destination")
    void movesDocumentFromConfiguredDocumentStoreSourceToDestination() throws Exception {
        Path documentRoot = Files.createTempDirectory(tempDir, "document-root-");
        Path sourceDir = Files.createDirectory(documentRoot.resolve("source"));
        Path destinationDir = Files.createDirectory(tempDir.resolve("destination"));
        String filename = "stored-document.pdf";
        Files.write(sourceDir.resolve(filename), new byte[]{4, 5, 6});

        CarlosProperties properties = CarlosProperties.getInstance();
        String previousDocumentDir = properties.getProperty("DOCUMENT_DIR");
        try {
            properties.setProperty("DOCUMENT_DIR", documentRoot.toString());

            Document document = new Document();
            document.setDocfilename(filename);
            document.setDocumentNo(43);

            manager.moveDocument(loggedInInfo, document, sourceDir.toString(), destinationDir.toString());

            assertThat(Files.exists(sourceDir.resolve(filename))).isFalse();
            assertThat(Files.readAllBytes(destinationDir.resolve(filename))).containsExactly(4, 5, 6);
        } finally {
            if (previousDocumentDir == null) {
                properties.remove("DOCUMENT_DIR");
            } else {
                properties.setProperty("DOCUMENT_DIR", previousDocumentDir);
            }
            Files.deleteIfExists(sourceDir.resolve(filename));
            Files.deleteIfExists(sourceDir);
            Files.deleteIfExists(documentRoot);
        }
    }

    @Test
    @DisplayName("reports missing configured document source instead of generic move error")
    void reportsMissingConfiguredDocumentSource_insteadOfGenericMoveError() throws Exception {
        Path documentRoot = Files.createTempDirectory(tempDir, "document-root-");
        Path sourceDir = Files.createDirectory(documentRoot.resolve("source"));
        Path destinationDir = Files.createDirectory(tempDir.resolve("destination"));
        String filename = "missing-document.pdf";

        CarlosProperties properties = CarlosProperties.getInstance();
        String previousDocumentDir = properties.getProperty("DOCUMENT_DIR");
        try {
            properties.setProperty("DOCUMENT_DIR", documentRoot.toString());

            Document document = new Document();
            document.setDocfilename(filename);
            document.setDocumentNo(44);

            assertThatThrownBy(() -> manager.moveDocument(loggedInInfo, document,
                    sourceDir.toString(), destinationDir.toString()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Invalid document move path")
                    .hasRootCauseMessage("Source document does not exist");
        } finally {
            if (previousDocumentDir == null) {
                properties.remove("DOCUMENT_DIR");
            } else {
                properties.setProperty("DOCUMENT_DIR", previousDocumentDir);
            }
            Files.deleteIfExists(sourceDir);
            Files.deleteIfExists(documentRoot);
        }
    }

    @Test
    @DisplayName("rejects stored document filename with path separator")
    void rejectsStoredDocumentFilenameWithPathSeparator() {
        Path sourceDir = tempDir.resolve("source");
        Path destinationDir = tempDir.resolve("destination");

        Document document = new Document();
        document.setDocfilename("../generated-document.pdf");
        document.setDocumentNo(42);
        String sourcePath = sourceDir.toString();
        String destinationPath = destinationDir.toString();

        assertThatThrownBy(() -> manager.moveDocument(loggedInInfo, document, sourcePath, destinationPath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid document move path");
    }
}
