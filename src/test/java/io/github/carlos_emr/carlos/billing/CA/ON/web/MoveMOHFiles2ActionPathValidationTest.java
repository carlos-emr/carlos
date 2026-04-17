/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.billing.CA.ON.web;

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
 * Unit tests for path validation in {@link MoveMOHFiles2Action}.
 *
 * @since 2026-04-17
 */
@DisplayName("MoveMOHFiles2Action Path Validation Tests")
@Tag("unit")
@Tag("security")
class MoveMOHFiles2ActionPathValidationTest extends CarlosUnitTestBase {

    @TempDir
    Path tempDir;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MoveMOHFiles2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        action = new MoveMOHFiles2Action();
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should throw SecurityException when MOH file contains path traversal")
    void shouldThrowSecurityException_whenMohFileContainsPathTraversal() {
        assertThatThrownBy(() -> invokeGetFile(tempDir.toString(), "../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid file path");
    }

    private File invokeGetFile(String folderPath, String fileName) throws Throwable {
        Method method = MoveMOHFiles2Action.class.getDeclaredMethod("getFile", String.class, String.class);
        method.setAccessible(true);
        try {
            return (File) method.invoke(action, folderPath, fileName);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
