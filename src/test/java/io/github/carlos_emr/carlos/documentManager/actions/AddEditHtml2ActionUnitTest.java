/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link AddEditHtml2Action}'s lowercase parameter alias.
 *
 * <p>The Add-Link and Add-HTML forms carry the same case-variant duplicate that orphaned eDocs
 * uploads: they post both {@code functionId} and {@code functionid}, Struts 7's case-insensitive
 * {@code HttpParameters} collapses the pair into the lowercase key, and the case-sensitive
 * {@code @StrutsParameter} lookup then matched no member and dropped the patient id — filing the
 * document against no chart at all.
 *
 * <p>{@link AddEditDocument2Action} got both the fix and a test; this class got the fix only, so
 * deleting its alias re-orphaned link and HTML documents with every check still green.
 *
 * @since 2026-08-30
 */
@DisplayName("AddEditHtml2Action parameter binding")
@Tag("unit")
@Tag("document")
class AddEditHtml2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;

    @BeforeEach
    void setUp() {
        // The action resolves SecurityInfoManager and the servlet request/response in field
        // initializers, so both have to be in place before the constructor runs.
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest)
                .thenReturn(new MockHttpServletRequest());
        servletActionContextMock.when(ServletActionContext::getResponse)
                .thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should bind lowercase functionid to the same property as functionId")
    void shouldBindLowercaseFunctionid_toSameProperty() throws NoSuchMethodException {
        AddEditHtml2Action action = new AddEditHtml2Action();

        action.setFunctionid("42");

        assertThat(action.getFunctionId()).isEqualTo("42");

        // Delegation alone is not enough: Struts binds a request parameter only through a setter
        // carrying @StrutsParameter, so removing just the annotation reopens the defect while
        // leaving the delegation assertion above green. Pin the annotation too.
        assertThat(AddEditHtml2Action.class.getMethod("setFunctionid", String.class)
                .isAnnotationPresent(StrutsParameter.class))
                .as("setFunctionid must be @StrutsParameter-annotated or Struts will not bind it")
                .isTrue();
    }
}
