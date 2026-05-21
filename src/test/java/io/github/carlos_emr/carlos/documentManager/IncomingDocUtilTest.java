/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IncomingDocUtil Unit Tests")
@Tag("unit")
@Tag("documentManager")
class IncomingDocUtilTest {

    @TempDir
    private Path incomingDocumentDir;

    @Test
    @DisplayName("should reject path components that need repeated-dot normalization")
    void shouldRejectPathComponentsThatNeedRepeatedDotNormalization() throws Exception {
        assertThat(isValidPathComponent("my..file.pdf")).isFalse();
    }

    @Test
    @DisplayName("should reject traversal and hidden path components")
    void shouldRejectTraversalAndHidden_pathComponents() throws Exception {
        assertThat(isValidPathComponent("/..")).isFalse();
        assertThat(isValidPathComponent("..")).isFalse();
        assertThat(isValidPathComponent(".env")).isFalse();
        assertThat(isValidPathComponent("nested/report.pdf")).isFalse();
    }

    @Test
    @DisplayName("should allow configured incoming document folder")
    void shouldAllowConfiguredIncomingDocumentFolder() {
        String previousIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        String previousAllowedFolders = CarlosProperties.getInstance().getProperty("ALLOWED_INCOMING_DOC_FOLDERS");
        try {
            CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incomingDocumentDir.toString());
            CarlosProperties.getInstance().setProperty("ALLOWED_INCOMING_DOC_FOLDERS", "Fax,Referral");

            String incomingPath = IncomingDocUtil.getIncomingDocumentFilePath("123", "Referral");

            assertThat(IncomingDocUtil.isAllowedIncomingDocumentFolder("Referral")).isTrue();
            assertThat(Path.of(incomingPath)).isEqualTo(incomingDocumentDir.resolve("123").resolve("Referral"));
        } finally {
            restoreProperty("INCOMINGDOCUMENT_DIR", previousIncomingDocumentDir);
            restoreProperty("ALLOWED_INCOMING_DOC_FOLDERS", previousAllowedFolders);
        }
    }

    @Test
    @DisplayName("should reject folder omitted from incoming document folder configuration")
    void shouldRejectFolderOmittedFromIncomingDocumentFolderConfiguration() {
        String previousIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        String previousAllowedFolders = CarlosProperties.getInstance().getProperty("ALLOWED_INCOMING_DOC_FOLDERS");
        try {
            CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incomingDocumentDir.toString());
            CarlosProperties.getInstance().setProperty("ALLOWED_INCOMING_DOC_FOLDERS", "Fax,Referral");

            assertThatThrownBy(() -> IncomingDocUtil.getIncomingDocumentFilePath("123", "Mail"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("configured incoming document folder");
        } finally {
            restoreProperty("INCOMINGDOCUMENT_DIR", previousIncomingDocumentDir);
            restoreProperty("ALLOWED_INCOMING_DOC_FOLDERS", previousAllowedFolders);
        }
    }

    private boolean isValidPathComponent(String pathComponent) throws Exception {
        Method method = IncomingDocUtil.class.getDeclaredMethod("isValidPathComponent", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, pathComponent);
    }

    private void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, previousValue);
        }
    }
}
