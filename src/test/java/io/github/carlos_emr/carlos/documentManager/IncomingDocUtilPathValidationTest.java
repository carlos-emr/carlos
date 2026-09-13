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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IncomingDocUtil Path Validation Tests")
@Tag("unit")
@Tag("fast")
@Tag("security")
class IncomingDocUtilPathValidationTest {

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
    @DisplayName("should resolve valid incoming document path under configured root")
    void shouldResolveValidIncomingDocumentPathUnderConfiguredRoot() {
        String path = IncomingDocUtil.getIncomingDocumentFilePathName("1", "Fax", "report.pdf");

        assertThat(path).isEqualTo(incomingRoot.resolve("1").resolve("Fax").resolve("report.pdf").toString());
    }

    @Test
    @DisplayName("should reject traversal in queue id before path construction")
    void shouldRejectTraversalInQueueIdBeforePathConstruction() {
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePathName("queueA/../queueB", "Fax", "report.pdf"))
            .isInstanceOf(FileValidationException.class)
            .hasMessageContaining("Invalid filename");
    }

    @Test
    @DisplayName("should reject traversal in pdf name before basename sanitization")
    void shouldRejectTraversalInPdfNameBeforeBasenameSanitization() {
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePathName("1", "Fax", "../report.pdf"))
            .isInstanceOf(FileValidationException.class)
            .hasMessageContaining("Invalid filename");
    }

    @Test
    @DisplayName("should reject non allowlisted incoming document directory")
    void shouldRejectNonAllowlistedIncomingDocumentDirectory() {
        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePathName("1", "Fax_deleted", "report.pdf"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid pdfDir");
    }
}
