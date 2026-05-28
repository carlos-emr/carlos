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
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link IncomingDocUtil}.
 *
 * @since 2026-05-28
 */
@DisplayName("IncomingDocUtil Unit Tests")
@Tag("unit")
@Tag("security")
@ResourceLock("CarlosProperties")
class IncomingDocUtilUnitTest {
    private static final String INCOMINGDOCUMENT_DIR = "INCOMINGDOCUMENT_DIR";
    private static final String ALLOWED_INCOMING_DOC_FOLDERS = "ALLOWED_INCOMING_DOC_FOLDERS";

    @TempDir
    Path tempDir;

    private String originalIncomingDocumentDir;
    private String originalAllowedIncomingDocFolders;

    @BeforeEach
    void setUp() {
        CarlosProperties properties = CarlosProperties.getInstance();
        originalIncomingDocumentDir = properties.getProperty(INCOMINGDOCUMENT_DIR);
        originalAllowedIncomingDocFolders = properties.getProperty(ALLOWED_INCOMING_DOC_FOLDERS, null);
        properties.setProperty(INCOMINGDOCUMENT_DIR, tempDir.toString());
        properties.remove(ALLOWED_INCOMING_DOC_FOLDERS);
    }

    @AfterEach
    void tearDown() {
        CarlosProperties properties = CarlosProperties.getInstance();
        restoreProperty(properties, INCOMINGDOCUMENT_DIR, originalIncomingDocumentDir);
        restoreProperty(properties, ALLOWED_INCOMING_DOC_FOLDERS, originalAllowedIncomingDocFolders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Fax", "Mail", "File", "Refile"})
    @DisplayName("should return folder path when folder is in default allowlist")
    void shouldReturnFolderPath_whenFolderIsInDefaultAllowlist(String pdfDir) {
        String path = IncomingDocUtil.getIncomingDocumentFilePath("queue1", pdfDir);

        assertThat(path).isEqualTo(tempDir + File.separator + "queue1" + File.separator + pdfDir);
    }

    @Test
    @DisplayName("should return configured folder path when folder is in configured allowlist")
    void shouldReturnConfiguredFolderPath_whenFolderIsInConfiguredAllowlist() {
        CarlosProperties.getInstance().setProperty(ALLOWED_INCOMING_DOC_FOLDERS, "Portal, Mailbox");

        String path = IncomingDocUtil.getIncomingDocumentFilePath("queue1", "Portal");

        assertThat(path).isEqualTo(tempDir + File.separator + "queue1" + File.separator + "Portal");
    }

    @Test
    @DisplayName("should reject default folder when configured allowlist excludes it")
    void shouldRejectDefaultFolder_whenConfiguredAllowlistExcludesIt() {
        CarlosProperties.getInstance().setProperty(ALLOWED_INCOMING_DOC_FOLDERS, "Portal,Mailbox");

        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePath("queue1", "Fax"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid pdfDir");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Unknown", "Fax/Archive", "Mail\\Archive"})
    @DisplayName("should reject invalid folder names when resolving incoming path")
    void shouldRejectInvalidFolderNames_whenResolvingIncomingPath(String pdfDir) {
        CarlosProperties.getInstance().setProperty(ALLOWED_INCOMING_DOC_FOLDERS,
                "Fax,Mail,File,Refile,Fax/Archive,Mail\\Archive");

        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePath("queue1", pdfDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid pdfDir");
    }

    private void restoreProperty(CarlosProperties properties, String key, String value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }
}
