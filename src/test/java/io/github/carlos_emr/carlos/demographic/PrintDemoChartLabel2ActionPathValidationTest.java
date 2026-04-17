/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.demographic;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for label path validation in {@link PrintDemoChartLabel2Action}.
 *
 * @since 2026-04-17
 */
@DisplayName("PrintDemoChartLabel2Action Path Validation Tests")
@Tag("unit")
@Tag("security")
class PrintDemoChartLabel2ActionPathValidationTest extends CarlosUnitTestBase {

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private PrintDemoChartLabel2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        action = new PrintDemoChartLabel2Action();
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should throw SecurityException when label file contains path traversal")
    void shouldThrowSecurityException_whenLabelFileContainsPathTraversal() {
        assertThatThrownBy(() -> invokeResolveUserHomeLabelFile("../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid label file");
    }

    private File invokeResolveUserHomeLabelFile(String labelFile) throws Throwable {
        Method method = PrintDemoChartLabel2Action.class.getDeclaredMethod("resolveUserHomeLabelFile", String.class);
        method.setAccessible(true);
        try {
            return (File) method.invoke(action, labelFile);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
