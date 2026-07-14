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
package io.github.carlos_emr.carlos.form.util;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FormTransportContainer")
@Tag("unit")
@Tag("form")
class FormTransportContainerUnitTest {

    @AfterEach
    void tearDown() {
        ActionContext.clear();
    }

    @Test
    void shouldCaptureForwardedFormHtml_whenNestedRenderUsesStrutsResponse() throws Exception {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        ActionContext.of().withServletResponse(outerResponse).bind();
        MockHttpServletRequest request = requestForwardingTo(servletResponse -> {
            assertThat(ServletActionContext.getResponse()).isSameAs(servletResponse);
            ServletActionContext.getResponse().getWriter().write("<html>form</html>");
            ServletActionContext.getResponse().flushBuffer();
        });

        FormTransportContainer container = new FormTransportContainer(outerResponse, request, "/form/formannual");

        assertThat(container.getHTML()).contains("<html>form</html>");
        assertThat(outerResponse.isCommitted()).isFalse();
        assertThat(outerResponse.getContentAsString()).isEmpty();
        assertThat(ServletActionContext.getResponse()).isSameAs(outerResponse);
    }

    @Test
    void shouldRejectNestedRedirect_withoutMutatingCallerResponse() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        ActionContext.of().withServletResponse(outerResponse).bind();
        MockHttpServletRequest request = requestForwardingTo(servletResponse -> {
            ServletActionContext.getResponse().sendRedirect("/carlos/form/formannual");
            assertThat(ServletActionContext.getResponse().isCommitted()).isTrue();
        });

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/forwardshortcutname"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 302");

        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(outerResponse.getRedirectedUrl()).isNull();
        assertThat(outerResponse.isCommitted()).isFalse();
        assertThat(ServletActionContext.getResponse()).isSameAs(outerResponse);
    }

    @Test
    void shouldRejectNestedError_withoutExposingNestedMessage() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        ActionContext.of().withServletResponse(outerResponse).bind();
        MockHttpServletRequest request = requestForwardingTo(servletResponse -> {
            ServletActionContext.getResponse().sendError(500, "patient-specific error");
            assertThat(ServletActionContext.getResponse().isCommitted()).isTrue();
        });

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/formannual"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 500")
                .hasMessageNotContaining("patient-specific error");

        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(outerResponse.isCommitted()).isFalse();
        assertThat(ServletActionContext.getResponse()).isSameAs(outerResponse);
    }

    @Test
    void shouldRejectNestedNoContent_withoutRenderingBlankAttachment() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        ActionContext.of().withServletResponse(outerResponse).bind();
        MockHttpServletRequest request = requestForwardingTo(servletResponse ->
                ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_NO_CONTENT));

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/formannual"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 204");

        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(outerResponse.isCommitted()).isFalse();
        assertThat(ServletActionContext.getResponse()).isSameAs(outerResponse);
    }

    private static MockHttpServletRequest requestForwardingTo(ForwardHandler forwardHandler) {
        return new MockHttpServletRequest() {
            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                return new RequestDispatcher() {
                    @Override
                    public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException {
                        forwardHandler.forward(response);
                    }

                    @Override
                    public void include(ServletRequest request, ServletResponse response) {
                        throw new UnsupportedOperationException("Test dispatcher does not support include");
                    }
                };
            }
        };
    }

    @FunctionalInterface
    private interface ForwardHandler {
        void forward(ServletResponse response) throws ServletException, IOException;
    }
}
