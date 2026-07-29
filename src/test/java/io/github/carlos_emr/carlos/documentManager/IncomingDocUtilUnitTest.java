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
import java.util.ArrayList;

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

    @TempDir
    Path incomingRoot;

    private String previousIncomingDocumentDir;

    @BeforeEach
    void setUp() {
        previousIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incomingRoot.toString());
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
    @DisplayName("should return an empty doc list when the queue directory does not exist yet")
    void shouldReturnEmptyDocList_whenQueueDirectoryDoesNotExist() {
        // A queue subdirectory is only created by the first upload or fax import; browsing
        // a fresh queue used to throw SecurityException and land on the error page.
        String missingDirectory = incomingRoot.resolve("1").resolve("Fax").toString();

        ArrayList<?> docList = new IncomingDocUtil().getDocList(missingDirectory);

        assertThat(docList).isEmpty();
    }

    @Test
    @DisplayName("should list queued PDF files when the queue directory exists")
    void shouldListQueuedPdfFiles_whenQueueDirectoryExists() throws Exception {
        File faxDir = incomingRoot.resolve("1").resolve("Fax").toFile();
        assertThat(faxDir.mkdirs()).isTrue();
        Files.createFile(faxDir.toPath().resolve("first.pdf"));
        Files.createFile(faxDir.toPath().resolve(PARENTHESIZED_NAME));
        Files.createFile(faxDir.toPath().resolve("notes.txt"));

        IncomingDocUtil incomingDocUtil = new IncomingDocUtil();
        @SuppressWarnings("unchecked")
        ArrayList<String> docList = incomingDocUtil.getDocList(faxDir.getPath());

        assertThat(docList)
                .describedAs("only PDF files are listed, under their exact on-disk names")
                .containsExactlyInAnyOrder("first.pdf", PARENTHESIZED_NAME);
        assertThat(incomingDocUtil.getPdfListModifiedDate()).hasSize(2);
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
}
