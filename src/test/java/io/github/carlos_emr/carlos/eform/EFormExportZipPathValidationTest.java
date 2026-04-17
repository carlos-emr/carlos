/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.eform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ZIP entry path validation in {@link EFormExportZip}.
 *
 * @since 2026-04-17
 */
@DisplayName("EFormExportZip Path Validation Tests")
@Tag("unit")
@Tag("security")
class EFormExportZipPathValidationTest {

    private final EFormExportZip exportZip = new EFormExportZip();

    @Test
    @DisplayName("should throw SecurityException when zip entry contains path traversal")
    void shouldThrowSecurityException_whenZipEntryContainsPathTraversal() {
        assertThatThrownBy(() -> invokeSanitizeZipEntryFileName("../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid zip entry");
    }

    private String invokeSanitizeZipEntryFileName(String entryName) throws Throwable {
        Method method = EFormExportZip.class.getDeclaredMethod("sanitizeZipEntryFileName", String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(exportZip, entryName);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
