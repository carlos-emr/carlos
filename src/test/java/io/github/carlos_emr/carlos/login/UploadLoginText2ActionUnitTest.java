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
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UploadLoginText2Action")
@Tag("unit")
@Tag("web")
@Tag("login")
class UploadLoginText2ActionUnitTest extends CarlosWebTestBase {

    @TempDir
    private Path documentDir;

    @TempDir
    private Path uploadDir;

    private String originalDocumentDir;

    @BeforeEach
    void setUpDocumentDir() {
        originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
    }

    @AfterEach
    void restoreDocumentDir() {
        if (originalDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
        }
    }

    @Test
    @DisplayName("should return success when upload file is missing")
    void shouldReturnSuccess_whenUploadFileIsMissing() throws Exception {
        addValidDurationParameters();
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(null);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(false);
        assertThat(documentDir.resolve("OSCARloginText.txt")).doesNotExist();
    }

    @Test
    @DisplayName("should write login text when upload file is present")
    void shouldWriteLoginText_whenUploadFileIsPresent() throws Exception {
        addValidDurationParameters();
        Path uploadFile = Files.createTempFile(uploadDir, "login-text-", ".txt");
        Files.writeString(uploadFile, "updated login text", StandardCharsets.UTF_8);
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(uploadFile.toFile());

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(false);
        assertThat(documentDir.resolve("OSCARloginText.txt"))
                .hasContent("updated login text");
    }

    @Test
    @DisplayName("should set error when configured document directory is invalid")
    void shouldSetError_whenDocumentDirectoryIsInvalid() throws Exception {
        addValidDurationParameters();
        Path invalidDocumentDir = Files.createTempFile(documentDir, "not-a-dir-", ".txt");
        Files.writeString(invalidDocumentDir, "not a directory", StandardCharsets.UTF_8);
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", invalidDocumentDir.toString());
        Path uploadFile = Files.createTempFile(uploadDir, "login-text-", ".txt");
        Files.writeString(uploadFile, "updated login text", StandardCharsets.UTF_8);
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(uploadFile.toFile());

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(true);
        assertThat(invalidDocumentDir).hasContent("not a directory");
    }

    @Test
    @DisplayName("should preserve existing login text when upload read fails")
    void shouldPreserveLoginText_whenUploadReadFails() throws Exception {
        addValidDurationParameters();
        Path existingLoginText = documentDir.resolve("OSCARloginText.txt");
        Files.writeString(existingLoginText, "existing login text", StandardCharsets.UTF_8);
        Path uploadFile = Files.createTempFile(uploadDir, "login-text-", ".txt");
        Files.writeString(uploadFile, "updated login text", StandardCharsets.UTF_8);
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(uploadFile.toFile());
        Files.delete(uploadFile);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(true);
        assertThat(existingLoginText).hasContent("existing login text");
        try (Stream<Path> documentFiles = Files.list(documentDir)) {
            assertThat(documentFiles)
                    .extracting(path -> path.getFileName().toString())
                    .containsExactly("OSCARloginText.txt");
        }
    }

    private void addValidDurationParameters() {
        addRequestParameter("validDurationNumber", "1");
        addRequestParameter("validDurationPeriod", "year");
    }
}
