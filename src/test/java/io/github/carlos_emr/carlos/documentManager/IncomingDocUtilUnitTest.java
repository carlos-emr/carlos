/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.CarlosProperties;

@Tag("unit")
@DisplayName("Incoming document utility")
class IncomingDocUtilUnitTest {

    @TempDir
    Path incomingDocumentDir;

    private String previousIncomingDocumentDir;
    private String previousAllowedFolders;

    @BeforeEach
    void setUp() {
        CarlosProperties properties = CarlosProperties.getInstance();
        previousIncomingDocumentDir = properties.getProperty("INCOMINGDOCUMENT_DIR");
        previousAllowedFolders = properties.getProperty(IncomingDocUtil.ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY);
        properties.setProperty("INCOMINGDOCUMENT_DIR", incomingDocumentDir.toString());
        properties.remove(IncomingDocUtil.ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        restoreProperty("INCOMINGDOCUMENT_DIR", previousIncomingDocumentDir);
        restoreProperty(IncomingDocUtil.ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY, previousAllowedFolders);
    }

    @Test
    @DisplayName("should use default incoming document folder allowlist when property is absent")
    void shouldUseDefaultIncomingDocumentFolderAllowlist_whenPropertyAbsent() {
        assertThat(IncomingDocUtil.getAllowedIncomingDocFolders())
                .containsExactly("Fax", "Mail", "File", "Refile");
    }

    @Test
    @DisplayName("should allow configured incoming document folders")
    void shouldAllowConfiguredIncomingDocumentFolders() {
        CarlosProperties.getInstance().setProperty(
                IncomingDocUtil.ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY, "Fax,Custom");

        String path = IncomingDocUtil.getIncomingDocumentFilePath("1", "Custom");

        assertThat(path).endsWith(File.separator + "1" + File.separator + "Custom");
    }

    @Test
    @DisplayName("should reject incoming document folders outside configured allowlist")
    void shouldRejectIncomingDocumentFoldersOutsideConfiguredAllowlist() {
        CarlosProperties.getInstance().setProperty(
                IncomingDocUtil.ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY, "Fax,Mail");

        assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePath("1", "Custom"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fax, Mail");
    }

    @Test
    @DisplayName("should ignore invalid configured incoming document folders")
    void shouldIgnoreInvalidConfiguredIncomingDocumentFolders() {
        CarlosProperties.getInstance().setProperty(
                IncomingDocUtil.ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY, "Fax,../bad");

        assertThat(IncomingDocUtil.getAllowedIncomingDocFolders())
                .containsExactly("Fax");
    }

    private void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            CarlosProperties.getInstance().remove(key);
            return;
        }
        CarlosProperties.getInstance().setProperty(key, previousValue);
    }
}
