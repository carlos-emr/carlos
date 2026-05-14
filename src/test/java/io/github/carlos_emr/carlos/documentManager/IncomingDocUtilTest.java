/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IncomingDocUtil Unit Tests")
@Tag("unit")
@Tag("documentManager")
class IncomingDocUtilTest {

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
