/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.report.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for document path validation in {@link GeneratePatientLetters2Action}.
 *
 * @since 2026-04-17
 */
@DisplayName("GeneratePatientLetters2Action Path Validation Tests")
@Tag("unit")
@Tag("security")
class GeneratePatientLetters2ActionPathValidationTest extends CarlosUnitTestBase {

    @TempDir
    Path tempDir;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private GeneratePatientLetters2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        action = new GeneratePatientLetters2Action();
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should throw SecurityException when generated document path contains traversal")
    void shouldThrowSecurityException_whenGeneratedDocumentPathContainsTraversal() {
        assertThatThrownBy(() -> invokeValidateDocumentFile("../etc/passwd", tempDir.toFile()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid document file path");
    }

    private File invokeValidateDocumentFile(String fileName, File documentDir) throws Throwable {
        Method method = GeneratePatientLetters2Action.class.getDeclaredMethod("validateDocumentFile", String.class, File.class);
        method.setAccessible(true);
        try {
            return (File) method.invoke(action, fileName, documentDir);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
