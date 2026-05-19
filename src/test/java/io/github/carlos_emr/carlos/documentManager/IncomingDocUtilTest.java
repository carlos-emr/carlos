/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.CarlosProperties;

import java.io.File;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IncomingDocUtil Unit Tests")
@Tag("unit")
@Tag("documentManager")
class IncomingDocUtilTest {
    private static final String ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY = "ALLOWED_INCOMING_DOC_FOLDERS";
    @TempDir
    private Path incomingDocumentDir;

    private String previousIncomingDocFoldersProperty;

    @BeforeEach
    void setUp() {
        previousIncomingDocFoldersProperty = CarlosProperties.getInstance().getProperty(ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        if (previousIncomingDocFoldersProperty == null) {
            CarlosProperties.getInstance().remove(ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY);
        } else {
            CarlosProperties.getInstance().setProperty(ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY, previousIncomingDocFoldersProperty);
        }
    }

    @Test
    @DisplayName("should return defaults when incoming folder property is not set")
    void shouldReturnDefaults_whenIncomingDocFoldersPropertyNotSet() throws Exception {
        CarlosProperties.getInstance().remove(ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY);

        Set<String> allowedFolders = IncomingDocUtil.getAllowedIncomingDocFolders();

        assertThat(allowedFolders).containsExactlyInAnyOrder("Fax", "Mail", "File", "Refile");
    }

    @Test
    @DisplayName("should parse configured incoming folder allowlist")
    void shouldParseConfiguredIncomingFolders() throws Exception {
        CarlosProperties.getInstance().setProperty(ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY, "Inbox,Archive, Fax,,");

        Set<String> allowedFolders = IncomingDocUtil.getAllowedIncomingDocFolders();

        assertThat(allowedFolders).containsExactlyInAnyOrder("Inbox", "Archive", "Fax");
        assertThat(IncomingDocUtil.isValidIncomingDocFolder("Archive")).isTrue();
        assertThat(IncomingDocUtil.isValidIncomingDocFolder("BadFolder")).isFalse();
    }

    @Test
    @DisplayName("should fallback to defaults when configured incoming folder list is blank")
    void shouldFallbackToDefaults_whenIncomingDocFoldersPropertyIsBlank() throws Exception {
        CarlosProperties.getInstance().setProperty(ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY, "   , ,");

        Set<String> allowedFolders = IncomingDocUtil.getAllowedIncomingDocFolders();

        assertThat(allowedFolders).containsExactlyInAnyOrder("Fax", "Mail", "File", "Refile");
    }

    @Test
    @DisplayName("should honor configured incoming folder allowlist when resolving incoming paths")
    void shouldHonorConfiguredIncomingFolderAllowlist() throws Exception {
        String configuredIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        try {
            CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", this.incomingDocumentDir.toString());

            CarlosProperties.getInstance().setProperty(ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY, "Inbox,Archive");

            String filePath = IncomingDocUtil.getIncomingDocumentFilePath("123", "Archive");
            String deletedPath = IncomingDocUtil.getIncomingDocumentDeletedFilePath("123", "Archive");
            String basePath = this.incomingDocumentDir.toString();

            assertThat(filePath).isEqualTo(basePath + File.separator + "123" + File.separator + "Archive");
            assertThat(deletedPath).isEqualTo(basePath + File.separator + "123" + File.separator + "Archive_deleted");
        } finally {
            if (configuredIncomingDocumentDir == null) {
                CarlosProperties.getInstance().remove("INCOMINGDOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", configuredIncomingDocumentDir);
            }
        }
    }

    @Test
    @DisplayName("should allow repeated dots inside filename component")
    void shouldAllowRepeatedDots_insideFilenameComponent() throws Exception {
        assertThat(isValidPathComponent("my..file.pdf")).isTrue();
    }

    @Test
    @DisplayName("should reject traversal and hidden path components")
    void shouldRejectTraversalAndHidden_pathComponents() throws Exception {
        assertThat(isValidPathComponent("/..")).isFalse();
        assertThat(isValidPathComponent("..")).isFalse();
        assertThat(isValidPathComponent(".env")).isFalse();
        assertThat(isValidPathComponent("nested/report.pdf")).isFalse();
    }

    private boolean isValidPathComponent(String pathComponent) throws Exception {
        Method method = IncomingDocUtil.class.getDeclaredMethod("isValidPathComponent", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, pathComponent);
    }
}
