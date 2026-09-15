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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.FileValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression tests for the incoming-docs queue flows behind issue #3238: a never-used
 * queue must render as empty rather than erroring, and queued documents must be
 * resolvable under their exact on-disk names.
 */
@DisplayName("IncomingDocUtil incoming queue listing and name resolution")
@Tag("unit")
@Tag("fast")
@Tag("document")
class IncomingDocUtilUnitTest {

    /** On-disk name shaped like a browser download copy — spaces and parentheses included. */
    private static final String PARENTHESIZED_NAME = "2026_07_08_scan (1).pdf";

    private static final String ALLOWED_FOLDERS_PROPERTY = "ALLOWED_INCOMING_DOC_FOLDERS";

    @TempDir
    Path incomingRoot;

    private String previousIncomingDocumentDir;
    private String previousAllowedFolders;

    @BeforeEach
    void setUp() {
        previousIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        previousAllowedFolders = CarlosProperties.getInstance().getProperty(ALLOWED_FOLDERS_PROPERTY);
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incomingRoot.toString());
        CarlosProperties.getInstance().remove(ALLOWED_FOLDERS_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        if (previousIncomingDocumentDir == null) {
            CarlosProperties.getInstance().remove("INCOMINGDOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", previousIncomingDocumentDir);
        }
        if (previousAllowedFolders == null) {
            CarlosProperties.getInstance().remove(ALLOWED_FOLDERS_PROPERTY);
        } else {
            CarlosProperties.getInstance().setProperty(ALLOWED_FOLDERS_PROPERTY, previousAllowedFolders);
        }
    }

    @Test
    @DisplayName("should return an empty doc list when the queue directory does not exist yet")
    void shouldReturnEmptyDocList_whenQueueDirectoryDoesNotExist() {
        // A queue subdirectory is only created by the first upload or fax import; browsing
        // a fresh queue used to throw SecurityException and land on the error page.
        String missingDirectory = incomingRoot.resolve("1").resolve("Fax").toString();

        List<String> docList = new IncomingDocUtil().getDocList(missingDirectory);

        assertThat(docList).isEmpty();
    }

    @Test
    @DisplayName("should create a missing queue directory when a write path is requested")
    void shouldCreateMissingDirectory_whenWritePathRequested() {
        String path = IncomingDocUtil.getAndCreateIncomingDocumentFilePath("1", "Refile");

        assertThat(Path.of(path)).isDirectory();
    }

    @Test
    @DisplayName("should fail when a write path cannot be created as a directory")
    void shouldFail_whenWritePathIsExistingFile() throws Exception {
        Files.createFile(incomingRoot.resolve("1"));

        assertThatThrownBy(() -> IncomingDocUtil.getAndCreateIncomingDocumentFilePath("1", "Refile"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to create incoming document directory");
    }

    @Test
    @DisplayName("should fail loudly instead of recreating a missing incoming document root")
    void shouldFailLoudly_whenWriteRootMissing() {
        Path missingRoot = incomingRoot.resolve("missing-root");
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", missingRoot.toString());

        assertThatThrownBy(() -> IncomingDocUtil.getAndCreateIncomingDocumentFilePath("1", "Refile"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Configured path is not a directory");
        assertThat(missingRoot).doesNotExist();
    }

    @Test
    @DisplayName("should reject a doc list directory that resolves outside the incoming root")
    void shouldRejectDocListDirectory_whenOutsideIncomingRoot(@TempDir Path outsideRoot) {
        // The containment check must run before any filesystem probe; without it the
        // empty-queue early return would happily probe arbitrary paths.
        IncomingDocUtil incomingDocUtil = new IncomingDocUtil();
        String outsideDirectory = outsideRoot.toString();

        assertThatThrownBy(() -> incomingDocUtil.getDocList(outsideDirectory))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should fail loudly when the incoming root itself is missing")
    void shouldFailLoudly_whenIncomingRootMissing() {
        // Only a missing queue SUBDIRECTORY is an empty queue. A missing base directory
        // (config typo, unmounted volume) rendering every queue as empty would hide
        // accumulating incoming documents from intake staff.
        Path missingBase = incomingRoot.resolve("missing-base");
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", missingBase.toString());
        IncomingDocUtil incomingDocUtil = new IncomingDocUtil();
        String missingQueueDirectory = missingBase.resolve("1").resolve("Fax").toString();

        assertThatThrownBy(() -> incomingDocUtil.getDocList(missingQueueDirectory))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should throw an illegal state when the incoming root is not configured")
    void shouldThrowIllegalState_whenIncomingRootUnconfigured() {
        CarlosProperties.getInstance().remove("INCOMINGDOCUMENT_DIR");
        IncomingDocUtil incomingDocUtil = new IncomingDocUtil();
        String queueDirectory = incomingRoot.resolve("1").resolve("Fax").toString();

        assertThatThrownBy(() -> incomingDocUtil.getDocList(queueDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INCOMINGDOCUMENT_DIR");
    }

    @Test
    @DisplayName("should list queued PDF files when the queue directory exists")
    void shouldListQueuedPdfFiles_whenQueueDirectoryExists() throws Exception {
        File faxDir = incomingRoot.resolve("1").resolve("Fax").toFile();
        assertThat(faxDir.mkdirs()).isTrue();
        Files.createFile(faxDir.toPath().resolve("first.pdf"));
        Files.createFile(faxDir.toPath().resolve(PARENTHESIZED_NAME));
        Files.createFile(faxDir.toPath().resolve(".hidden.pdf"));
        Files.createFile(faxDir.toPath().resolve("notes.txt"));

        IncomingDocUtil incomingDocUtil = new IncomingDocUtil();
        List<String> docList = incomingDocUtil.getDocList(faxDir.getPath());

        assertThat(docList)
                .describedAs("only PDF files are listed, under their exact on-disk names")
                .containsExactlyInAnyOrder("first.pdf", PARENTHESIZED_NAME);
        assertThat(incomingDocUtil.getPdfListModifiedDate()).hasSize(2);
    }

    @Test
    @DisplayName("should omit queued PDFs whose names cannot be read safely")
    void shouldOmitQueuedPdfFiles_whenNameFailsReadValidation() throws Exception {
        // These are legal single filenames on Unix but validatePathComponent correctly
        // treats them as path-bearing input. Listing them created rows that always failed
        // when selected. Windows cannot create these fixture names in the first place.
        assumeTrue(File.separatorChar == '/');
        File faxDir = incomingRoot.resolve("1").resolve("Fax").toFile();
        assertThat(faxDir.mkdirs()).isTrue();
        Files.createFile(faxDir.toPath().resolve("nested\\report.pdf"));
        Files.createFile(faxDir.toPath().resolve("C:report.pdf"));

        IncomingDocUtil incomingDocUtil = new IncomingDocUtil();

        assertThat(incomingDocUtil.getDocList(faxDir.getPath())).isEmpty();
        assertThat(incomingDocUtil.getPdfListModifiedDate()).isEmpty();
    }

    @Test
    @DisplayName("should omit queued PDF symlinks whose targets are outside the incoming root")
    void shouldOmitQueuedPdfSymlink_whenTargetOutsideIncomingRoot(@TempDir Path outsideRoot) throws Exception {
        assumeTrue(File.separatorChar == '/');
        File faxDir = incomingRoot.resolve("1").resolve("Fax").toFile();
        assertThat(faxDir.mkdirs()).isTrue();
        Files.createFile(faxDir.toPath().resolve("local.pdf"));
        Path outsidePdf = Files.createFile(outsideRoot.resolve("outside.pdf"));
        Files.createSymbolicLink(faxDir.toPath().resolve("linked.pdf"), outsidePdf);

        IncomingDocUtil incomingDocUtil = new IncomingDocUtil();

        assertThat(incomingDocUtil.getDocList(faxDir.getPath())).containsExactly("local.pdf");
        assertThat(incomingDocUtil.getPdfListModifiedDate()).hasSize(1);
    }

    @Test
    @DisplayName("should not expose mutable modification-date state")
    void shouldNotExposeMutableModificationDates() throws Exception {
        File faxDir = incomingRoot.resolve("1").resolve("Fax").toFile();
        assertThat(faxDir.mkdirs()).isTrue();
        Files.createFile(faxDir.toPath().resolve("first.pdf"));

        IncomingDocUtil incomingDocUtil = new IncomingDocUtil();
        incomingDocUtil.getDocList(faxDir.getPath());

        assertThatThrownBy(() -> incomingDocUtil.getPdfListModifiedDate().add("injected"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(incomingDocUtil.getPdfListModifiedDate()).hasSize(1);
    }

    @Test
    @DisplayName("should preserve the exact on-disk name when resolving a queued document path")
    void shouldPreserveExactFileName_whenResolvingQueuedDocumentPath() {
        // The listing exposes real on-disk names; normalizing them on the read path made
        // every name with spaces or parentheses unresolvable ("The PDF could be corrupted").
        String path = IncomingDocUtil.getIncomingDocumentFilePathName("1", "Fax", PARENTHESIZED_NAME);

        assertThat(path).isEqualTo(
                incomingRoot.resolve("1").resolve("Fax").resolve(PARENTHESIZED_NAME).toString());
    }

    @Test
    @DisplayName("should resolve the page count for a queued PDF whose name contains parentheses")
    void shouldResolvePageCount_forPdfNameWithParentheses() throws Exception {
        File faxDir = incomingRoot.resolve("1").resolve("Fax").toFile();
        assertThat(faxDir.mkdirs()).isTrue();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(new File(faxDir, PARENTHESIZED_NAME));
        }

        int numOfPages = IncomingDocUtil.getNumOfPages("1", "Fax", PARENTHESIZED_NAME);

        assertThat(numOfPages).isEqualTo(1);
    }

    @Test
    @DisplayName("should still reject a pdf name containing a parent directory traversal")
    void shouldRejectPdfName_withParentDirectoryTraversal() {
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePathName("1", "Fax", "../escape.pdf"))
                .isInstanceOf(FileValidationException.class);
    }

    @Test
    @DisplayName("should still reject a pdf name containing a path separator")
    void shouldRejectPdfName_withPathSeparator() {
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePathName("1", "Fax", "sub/escape.pdf"))
                .isInstanceOf(FileValidationException.class);
    }

    @Test
    @DisplayName("should reject a queued document name that is not a pdf")
    void shouldRejectPdfName_withNonPdfExtension() {
        // Preserving the exact name costs the normalizing validator's extension allowlist,
        // so the queue keeps its own: request-supplied names cannot address a .jsp or any
        // other non-PDF sitting in the queue directory.
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePathName("1", "Fax", "shell.jsp"))
                .isInstanceOf(FileValidationException.class);
    }

    @Test
    @DisplayName("should accept an uppercase pdf extension as a queued document name")
    void shouldAcceptPdfName_withUppercaseExtension() {
        // getDocList lists names case-insensitively, so resolution must agree with it.
        String path = IncomingDocUtil.getIncomingDocumentFilePathName("1", "Fax", "SCAN.PDF");

        assertThat(path).isEqualTo(incomingRoot.resolve("1").resolve("Fax").resolve("SCAN.PDF").toString());
    }

    @Test
    @DisplayName("should delete one page from a queued document with an uppercase pdf extension")
    void shouldDeletePage_fromPdfNameWithUppercaseExtension() throws Exception {
        File faxDir = incomingRoot.resolve("1").resolve("Fax").toFile();
        assertThat(faxDir.mkdirs()).isTrue();
        File pdf = new File(faxDir, "SCAN.PDF");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(pdf);
        }

        IncomingDocUtil.deletePage("1", "Fax", pdf.getName(), "1");

        assertThat(IncomingDocUtil.getNumOfPages("1", "Fax", pdf.getName())).isEqualTo(1);
    }

    @Test
    @DisplayName("should allow the shipped queue folders when no allowlist is configured")
    void shouldAllowShippedFolders_whenAllowlistUnconfigured() {
        assertThat(IncomingDocUtil.getAllowedIncomingDocFolders())
                .containsExactly("Fax", "Mail", "File", "Refile");
        assertThat(IncomingDocUtil.getDefaultIncomingDocFolder()).isEqualTo("Fax");
    }

    @Test
    @DisplayName("should resolve a queue path for every shipped folder")
    void shouldResolveQueuePath_forEveryShippedFolder() {
        for (String folder : IncomingDocUtil.getAllowedIncomingDocFolders()) {
            assertThat(IncomingDocUtil.getIncomingDocumentFilePath("1", folder))
                    .isEqualTo(incomingRoot.resolve("1").resolve(folder).toString());
        }
    }

    @Test
    @DisplayName("should resolve a queue path for a configured folder")
    void shouldResolveQueuePath_forConfiguredFolder() {
        CarlosProperties.getInstance().setProperty(ALLOWED_FOLDERS_PROPERTY, "Portal, Mailbox");

        assertThat(IncomingDocUtil.getAllowedIncomingDocFolders()).containsExactly("Portal", "Mailbox");
        assertThat(IncomingDocUtil.getIncomingDocumentFilePath("1", "Portal"))
                .isEqualTo(incomingRoot.resolve("1").resolve("Portal").toString());
    }

    @Test
    @DisplayName("should reject a shipped folder when the configured allowlist drops it")
    void shouldRejectShippedFolder_whenConfiguredAllowlistDropsIt() {
        // A configured list replaces the shipped one rather than extending it, so an install
        // can narrow where intake writes land.
        CarlosProperties.getInstance().setProperty(ALLOWED_FOLDERS_PROPERTY, "Portal,Mailbox");

        assertThat(IncomingDocUtil.isAllowedIncomingDocFolder("Fax")).isFalse();
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePath("1", "Fax"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid pdfDir");
    }

    @Test
    @DisplayName("should follow the configured allowlist when choosing the default folder")
    void shouldFollowConfiguredAllowlist_whenChoosingDefaultFolder() {
        // The incoming-docs screen falls back to this folder on first load; a default the
        // allowlist rejects would throw before the page rendered.
        CarlosProperties.getInstance().setProperty(ALLOWED_FOLDERS_PROPERTY, "Portal,Mailbox");

        String defaultFolder = IncomingDocUtil.getDefaultIncomingDocFolder();

        assertThat(defaultFolder).isEqualTo("Portal");
        assertThat(IncomingDocUtil.isAllowedIncomingDocFolder(defaultFolder)).isTrue();
    }

    @Test
    @DisplayName("should drop configured folder names that are not a single path component")
    void shouldDropConfiguredFolderNames_whenNotASinglePathComponent() {
        // Configuration must not be able to turn one path component into a subtree, and the
        // surviving names have to stay usable: callers take the first one as the UI default.
        CarlosProperties.getInstance().setProperty(ALLOWED_FOLDERS_PROPERTY,
                "Fax/Archive,Mail\\Archive,../escape,Portal");

        assertThat(IncomingDocUtil.getAllowedIncomingDocFolders()).containsExactly("Portal");
        assertThat(IncomingDocUtil.getDefaultIncomingDocFolder()).isEqualTo("Portal");
        assertThat(IncomingDocUtil.isAllowedIncomingDocFolder("Fax/Archive")).isFalse();
        assertThat(IncomingDocUtil.isAllowedIncomingDocFolder("Mail\\Archive")).isFalse();
        assertThat(IncomingDocUtil.isAllowedIncomingDocFolder("../escape")).isFalse();
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePath("1", "Fax/Archive"))
                .isInstanceOf(FileValidationException.class);
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePath("1", "../escape"))
                .isInstanceOf(FileValidationException.class);
    }

    @Test
    @DisplayName("should keep the shipped folders when the configured allowlist has nothing usable")
    void shouldKeepShippedFolders_whenConfiguredAllowlistUnusable() {
        // A typo that empties the list must not lock intake staff out of every queue.
        CarlosProperties.getInstance().setProperty(ALLOWED_FOLDERS_PROPERTY, " , ,");

        assertThat(IncomingDocUtil.getAllowedIncomingDocFolders())
                .containsExactly("Fax", "Mail", "File", "Refile");
    }

    @Test
    @DisplayName("should reject a folder that is not on the allowlist")
    void shouldRejectFolder_whenNotOnAllowlist() {
        assertThat(IncomingDocUtil.isAllowedIncomingDocFolder("Unknown")).isFalse();
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePath("1", "Unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid pdfDir");
    }

    @Test
    @DisplayName("should reject a blank folder name for the allowlist check")
    void shouldRejectBlankFolderName_forAllowlistCheck() {
        // getIncomingDocumentFilePath treats null/empty as "the queue root"; the boolean check
        // callers use to pick a UI default must not report that as an allowed folder.
        assertThat(IncomingDocUtil.isAllowedIncomingDocFolder(null)).isFalse();
        assertThat(IncomingDocUtil.isAllowedIncomingDocFolder("")).isFalse();
    }

    @Test
    @DisplayName("should label the shipped folders and leave configured folders unlabelled")
    void shouldLabelShippedFolders_andLeaveConfiguredFoldersUnlabelled() {
        assertThat(IncomingDocUtil.getIncomingDocFolderLabelKey("Fax")).isEqualTo("dms.incomingDocs.fax");
        assertThat(IncomingDocUtil.getIncomingDocFolderLabelKey("Refile")).isEqualTo("dms.incomingDocs.refile");
        assertThat(IncomingDocUtil.getIncomingDocFolderLabelKey("Portal")).isNull();
    }
}
