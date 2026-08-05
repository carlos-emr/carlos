/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.model.Queue;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.CarlosProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the refile-queue lookup behind issue #3239: a queue whose refile
 * directory has not been created yet must read as "nothing refiled" instead of breaking
 * every document view, and refiled names must be resolved exactly as refileDocument wrote
 * them, from the stored filename rather than the free-text description.
 */
@DisplayName("EDocUtil refile queue lookup")
@Tag("unit")
@Tag("fast")
@Tag("document")
class EDocUtilRefileQueueUnitTest extends CarlosUnitTestBase {

    /** Stored document filename: 14-char timestamp prefix, then the uploaded name. */
    private static final String STORED_FILENAME = "20260708120000scan (1).pdf";

    /** What refileDocument writes for the filename above: "R" + the name past the prefix. */
    private static final String REFILED_NAME = "Rscan (1).pdf";

    @TempDir
    Path incomingRoot;

    private String previousIncomingDocumentDir;
    private QueueDao queueDao;

    @BeforeEach
    void setUp() {
        previousIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incomingRoot.toString());
        queueDao = mock(QueueDao.class);
        registerMock(QueueDao.class, queueDao);
        when(queueDao.find(1)).thenReturn(new Queue());
    }

    @AfterEach
    void tearDown() {
        if (previousIncomingDocumentDir == null) {
            CarlosProperties.getInstance().remove("INCOMINGDOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", previousIncomingDocumentDir);
        }
    }

    @Test
    @DisplayName("should report nothing refiled when the queue has no refile directory yet")
    void shouldReportNotRefiled_whenRefileDirectoryMissing() {
        // showDocument.jsp calls this for every queue while rendering, so throwing here
        // broke viewing any document as soon as one queue had never received a refile.
        assertThatCode(() -> EDocUtil.isDocumentAlreadyRefiledInQueue(STORED_FILENAME, 1))
                .doesNotThrowAnyException();
        assertThat(EDocUtil.isDocumentAlreadyRefiledInQueue(STORED_FILENAME, 1)).isFalse();
    }

    @Test
    @DisplayName("should report nothing refiled when the refile directory holds no matching file")
    void shouldReportNotRefiled_whenRefiledFileAbsent() throws Exception {
        Files.createDirectories(refileDir().toPath());

        assertThat(EDocUtil.isDocumentAlreadyRefiledInQueue(STORED_FILENAME, 1)).isFalse();
    }

    @Test
    @DisplayName("should detect a refiled document whose name contains spaces and parentheses")
    void shouldDetectRefiledDocument_withParenthesizedName() throws Exception {
        File refileDir = refileDir();
        Files.createDirectories(refileDir.toPath());
        Files.createFile(refileDir.toPath().resolve(REFILED_NAME));

        // Normalizing the looked-up name produced "Rscan_1.pdf", which refileDocument never
        // writes, so an already-refiled document kept reporting as not refiled.
        assertThat(EDocUtil.isDocumentAlreadyRefiledInQueue(STORED_FILENAME, 1)).isTrue();
    }

    @Test
    @DisplayName("should detect a refiled document whose name is plain")
    void shouldDetectRefiledDocument_withPlainName() throws Exception {
        File refileDir = refileDir();
        Files.createDirectories(refileDir.toPath());
        Files.createFile(refileDir.toPath().resolve("Rreport.pdf"));

        assertThat(EDocUtil.isDocumentAlreadyRefiledInQueue("20260708120000report.pdf", 1)).isTrue();
    }

    @Test
    @DisplayName("should report nothing refiled when the filename carries a blocked extension")
    void shouldReportNotRefiled_whenFilenameHasBlockedExtension() throws Exception {
        Files.createDirectories(refileDir().toPath());

        // The path validator rejects blocked final extensions, and that rejection must not
        // escape a read-only predicate that runs while every document view renders.
        assertThat(EDocUtil.isDocumentAlreadyRefiledInQueue("20260708120000payload.jsp", 1)).isFalse();
    }

    @Test
    @DisplayName("should report nothing refiled when the document has no filename")
    void shouldReportNotRefiled_whenFilenameBlank() throws Exception {
        Files.createDirectories(refileDir().toPath());

        // HTML documents carry no filename at all.
        assertThat(EDocUtil.isDocumentAlreadyRefiledInQueue("   ", 1)).isFalse();
        assertThat(EDocUtil.isDocumentAlreadyRefiledInQueue(null, 1)).isFalse();
    }

    @Test
    @DisplayName("should not match a normalized spelling of the refiled name")
    void shouldNotMatchRefiledDocument_byNormalizedName() throws Exception {
        File refileDir = refileDir();
        Files.createDirectories(refileDir.toPath());
        // What the old normalizing lookup searched for. refileDocument never writes this,
        // so matching it would resurrect the mismatch this fix removed.
        Files.createFile(refileDir.toPath().resolve("Rscan_1.pdf"));

        assertThat(EDocUtil.isDocumentAlreadyRefiledInQueue(STORED_FILENAME, 1)).isFalse();
    }

    @Test
    @DisplayName("should never overwrite an existing refile destination")
    void shouldNotOverwriteExistingRefileDestination() throws Exception {
        Path source = Files.writeString(incomingRoot.resolve("source.pdf"), "new content");
        Path destination = Files.writeString(incomingRoot.resolve("destination.pdf"), "existing content");

        assertThatThrownBy(() -> EDocUtil.copyRefiledDocument(source.toFile(), destination.toFile()))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(destination)).isEqualTo("existing content");
    }

    @Test
    @DisplayName("should preserve the source timestamp when refiling")
    void shouldPreserveSourceTimestamp_whenRefiling() throws Exception {
        Path source = Files.writeString(incomingRoot.resolve("source.pdf"), "content");
        Path destination = incomingRoot.resolve("destination.pdf");
        long sourceTimestamp = 1_700_000_000_000L;
        assertThat(source.toFile().setLastModified(sourceTimestamp)).isTrue();

        EDocUtil.copyRefiledDocument(source.toFile(), destination.toFile());

        assertThat(destination.toFile().lastModified()).isEqualTo(sourceTimestamp);
    }

    @Test
    @DisplayName("should create a missing refile directory for the first refile into a queue")
    void shouldCreateMissingRefileDirectory_whenPreparingFirstRefile() throws Exception {
        Path source = Files.writeString(incomingRoot.resolve(STORED_FILENAME), "content");

        File destination = EDocUtil.prepareRefileDestination(source.toFile(), "1");
        EDocUtil.copyRefiledDocument(source.toFile(), destination);

        assertThat(destination.toPath())
                .exists()
                .hasFileName(REFILED_NAME);
        assertThat(destination.getParentFile()).isDirectory();
    }

    @Test
    @DisplayName("should reject a nonexistent queue without creating its refile directory")
    void shouldRejectNonexistentQueue_withoutCreatingDirectory() {
        File source = incomingRoot.resolve(STORED_FILENAME).toFile();

        assertThatThrownBy(() -> EDocUtil.prepareRefileDestination(source, "99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Queue not found");
        assertThat(incomingRoot.resolve("99")).doesNotExist();
    }

    @Test
    @DisplayName("should reject a noncanonical queue identifier before filesystem creation")
    void shouldRejectNoncanonicalQueueId_withoutCreatingDirectory() {
        File source = incomingRoot.resolve(STORED_FILENAME).toFile();

        assertThatThrownBy(() -> EDocUtil.prepareRefileDestination(source, "01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("queueId must be a positive integer");
        assertThat(incomingRoot.resolve("01")).doesNotExist();
    }

    private File refileDir() {
        return incomingRoot.resolve("1").resolve("Refile").toFile();
    }
}
