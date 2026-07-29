/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.form.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("FormTransportContainer")
@Tag("unit")
class FormTransportContainerUnitTest {

    @Test
    @DisplayName("captures nested form sendError without mutating the caller response")
    void shouldCaptureNestedSendError_withoutMutatingCallerResponse() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid form path"));

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/bad"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 400");

        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(outerResponse.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("captures nested form errors sent through the Struts response context")
    void shouldCaptureNestedStrutsSendError_withoutMutatingCallerResponse() {
        ActionContext previousContext = ActionContext.getContext();
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        ActionContext.of().withServletResponse(outerResponse).bind();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) ->
                ServletActionContext.getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid form path"));

        try {
            assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/bad"))
                    .isInstanceOf(ServletException.class)
                    .hasMessageContaining("HTTP status 400");

            assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(outerResponse.getErrorMessage()).isNull();
            assertThat(ServletActionContext.getResponse()).isSameAs(outerResponse);
        } finally {
            restoreActionContext(previousContext);
        }
    }

    @Test
    @DisplayName("does not expose the nested form error message in the thrown exception")
    void shouldNotExposeNestedErrorMessage_whenForwardSendsErrorWithMessage() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Failed to query form data"));

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/bad"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 500")
                .hasMessageNotContaining("Failed to query form data");

        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(outerResponse.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("does not carry a stale error message when a later sendError omits one")
    void shouldClearErrorMessage_whenLaterSendErrorOmitsMessage() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to query form data");
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
        });

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/bad"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 400")
                .hasMessageNotContaining("Failed to query form data");
    }

    @Test
    @DisplayName("decodes captured bytes using the charset supplied via setContentType")
    void shouldDecodeCapturedBytes_withCharsetFromContentType() throws Exception {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        outerResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html; charset=ISO-8859-1");
            servletResponse.getOutputStream().write("café".getBytes(StandardCharsets.ISO_8859_1));
        });

        FormTransportContainer container = new FormTransportContainer(outerResponse, request, "/form/charset");

        assertThat(container.getHTML()).contains("café");
    }

    @Test
    @DisplayName("captures nested flushBuffer without committing the caller response")
    void shouldCaptureNestedFlushBuffer_withoutCommittingCallerResponse() throws Exception {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            servletResponse.getWriter().write("<html>form</html>");
            servletResponse.flushBuffer();
        });

        FormTransportContainer container = new FormTransportContainer(outerResponse, request, "/form/flush");

        assertThat(container.getHTML()).contains("<html>form</html>");
        assertThat(outerResponse.isCommitted()).isFalse();
        assertThat(outerResponse.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("captures nested form output stream bytes without mutating the caller response")
    void shouldCaptureNestedOutputStream_withoutMutatingCallerResponse() throws Exception {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            servletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            servletResponse.getOutputStream().write("<html>stream form</html>".getBytes(StandardCharsets.UTF_8));
        });

        FormTransportContainer container = new FormTransportContainer(outerResponse, request, "/form/output");

        assertThat(container.getHTML()).contains("<html>stream form</html>");
        assertThat(outerResponse.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("rejects nested form output streams after writer access")
    void shouldRejectOutputStream_whenWriterAlreadyAccessed() throws Exception {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            servletResponse.getWriter().write("<html>writer form</html>");
            servletResponse.getOutputStream();
        });

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/output"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getWriter() has already been called");

        assertThat(outerResponse.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("rejects nested form writers after output stream access")
    void shouldRejectWriter_whenOutputStreamAlreadyAccessed() throws Exception {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            servletResponse.getOutputStream().write("<html>stream form</html>".getBytes(StandardCharsets.UTF_8));
            servletResponse.getWriter();
        });

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/output"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getOutputStream() has already been called");

        assertThat(outerResponse.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("rejects async write listeners for synchronous form capture")
    void shouldRejectAsyncWriteListener_forSynchronousCapture() throws Exception {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            ServletOutputStream outputStream = servletResponse.getOutputStream();

            assertThat(outputStream.isReady()).isTrue();
            assertThatThrownBy(() -> outputStream.setWriteListener(new WriteListener() {
                @Override
                public void onWritePossible() {
                    throw new AssertionError("Async callback should not run");
                }

                @Override
                public void onError(Throwable throwable) {
                    throw new AssertionError("Async error callback should not run", throwable);
                }
            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not support asynchronous writes");
        });

        FormTransportContainer container = new FormTransportContainer(outerResponse, request, "/form/output");

        assertThat(container.getHTML()).isEmpty();
        assertThat(outerResponse.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("keeps nested response metadata local to the form render")
    void shouldKeepNestedResponseMetadataLocal_whenForwardMutatesResponseMetadata() throws Exception {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        outerResponse.setLocale(Locale.CANADA);
        outerResponse.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        outerResponse.setContentType("text/plain");
        outerResponse.setBufferSize(8192);
        String outerContentType = outerResponse.getContentType();
        AtomicReference<Locale> nestedLocale = new AtomicReference<>();
        AtomicReference<String> nestedCharacterEncoding = new AtomicReference<>();
        AtomicReference<String> nestedContentType = new AtomicReference<>();
        AtomicReference<Integer> nestedBufferSize = new AtomicReference<>();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            servletResponse.setLocale(Locale.FRANCE);
            servletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            servletResponse.setContentType("text/html");
            servletResponse.setBufferSize(1024);
            nestedLocale.set(servletResponse.getLocale());
            nestedCharacterEncoding.set(servletResponse.getCharacterEncoding());
            nestedContentType.set(servletResponse.getContentType());
            nestedBufferSize.set(servletResponse.getBufferSize());
            servletResponse.getWriter().write("<html>metadata form</html>");
        });

        FormTransportContainer container = new FormTransportContainer(outerResponse, request, "/form/metadata");

        assertThat(container.getHTML()).contains("<html>metadata form</html>");
        assertThat(nestedLocale).hasValue(Locale.FRANCE);
        assertThat(nestedCharacterEncoding).hasValue(StandardCharsets.UTF_8.name());
        assertThat(nestedContentType).hasValue("text/html");
        assertThat(nestedBufferSize).hasValue(1024);
        assertThat(outerResponse.getLocale()).isEqualTo(Locale.CANADA);
        assertThat(outerResponse.getCharacterEncoding()).isEqualTo(StandardCharsets.ISO_8859_1.name());
        assertThat(outerResponse.getContentType()).isEqualTo(outerContentType);
        assertThat(outerResponse.getBufferSize()).isEqualTo(8192);
    }

    @Test
    @DisplayName("captures nested Struts flushBuffer without committing the caller response")
    void shouldCaptureNestedStrutsFlushBuffer_withoutCommittingCallerResponse() throws Exception {
        ActionContext previousContext = ActionContext.getContext();
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        ActionContext.of().withServletResponse(outerResponse).bind();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            ServletActionContext.getResponse().getWriter().write("<html>form</html>");
            ServletActionContext.getResponse().flushBuffer();
        });

        try {
            FormTransportContainer container = new FormTransportContainer(outerResponse, request, "/form/flush");

            assertThat(container.getHTML()).contains("<html>form</html>");
            assertThat(outerResponse.isCommitted()).isFalse();
            assertThat(outerResponse.getContentAsString()).isEmpty();
            assertThat(ServletActionContext.getResponse()).isSameAs(outerResponse);
        } finally {
            restoreActionContext(previousContext);
        }
    }

    @Test
    @DisplayName("rejects nested form redirects without mutating the caller response")
    void shouldRejectNestedRedirect_withoutMutatingCallerResponse() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendRedirect("/carlos/form/annual"));

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/forwardshortcutname"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 302");

        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(outerResponse.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("rejects nested form redirects sent through the clear-buffer overload")
    void shouldRejectNestedRedirectClearBufferOverload_withoutMutatingCallerResponse() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendRedirect("/carlos/form/annual", true));

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/forwardshortcutname"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 302");

        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(outerResponse.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("rejects nested form redirects sent through the status-code overload")
    void shouldRejectNestedRedirectStatusOverload_withoutMutatingCallerResponse() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendRedirect("/carlos/form/annual",
                        HttpServletResponse.SC_MOVED_PERMANENTLY));

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/forwardshortcutname"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 301");

        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(outerResponse.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("rejects nested form redirects sent through the status-code and clear-buffer overload")
    void shouldRejectNestedRedirectStatusAndClearBufferOverload_withoutMutatingCallerResponse() {
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendRedirect("/carlos/form/annual",
                        HttpServletResponse.SC_TEMPORARY_REDIRECT, false));

        assertThatThrownBy(() -> new FormTransportContainer(outerResponse, request, "/form/forwardshortcutname"))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("HTTP status 307");

        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(outerResponse.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("keeps nested trailer fields local to the form render")
    void shouldKeepNestedTrailerFieldsLocal_whenForwardSetsTrailerFields() throws Exception {
        AtomicBoolean outerTrailerFieldsSet = new AtomicBoolean(false);
        MockHttpServletResponse outerResponse = new MockHttpServletResponse() {
            @Override
            public void setTrailerFields(java.util.function.Supplier<java.util.Map<String, String>> supplier) {
                outerTrailerFieldsSet.set(true);
            }
        };
        AtomicReference<java.util.function.Supplier<java.util.Map<String, String>>> nestedTrailerFields =
                new AtomicReference<>();
        MockHttpServletRequest request = requestForwardingTo((servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setTrailerFields(() -> java.util.Map.of("X-Form-Trace", "nested"));
            nestedTrailerFields.set(httpResponse.getTrailerFields());
            servletResponse.getWriter().write("<html>trailer form</html>");
        });

        FormTransportContainer container = new FormTransportContainer(outerResponse, request, "/form/trailer");

        assertThat(container.getHTML()).contains("<html>trailer form</html>");
        assertThat(nestedTrailerFields.get()).isNotNull();
        assertThat(nestedTrailerFields.get().get()).containsEntry("X-Form-Trace", "nested");
        assertThat(outerTrailerFieldsSet).isFalse();
        assertThat(outerResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("does not reuse a custom forward path for later default renders")
    void shouldUseDefaultForwardPath_afterCustomForwardPathRender() throws Exception {
        new FormTransportContainer(new MockHttpServletResponse(), requestForwardingTo((request, response) ->
                ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_OK)), "/form/custom");

        AtomicReference<String> defaultPath = new AtomicReference<>();
        new FormTransportContainer(new MockHttpServletResponse(), requestRecordingForwardPath(defaultPath));

        assertThat(defaultPath).hasValue("/form/forwardshortcutname");
    }

    private MockHttpServletRequest requestForwardingTo(ForwardBehavior behavior) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                return new RequestDispatcher() {
                    @Override
                    public void forward(ServletRequest servletRequest, ServletResponse servletResponse)
                            throws ServletException, IOException {
                        behavior.forward(servletRequest, servletResponse);
                    }

                    @Override
                    public void include(ServletRequest servletRequest, ServletResponse servletResponse) {
                        throw new UnsupportedOperationException("include is not used by this test");
                    }
                };
            }
        };
        request.setContextPath("/carlos");
        return request;
    }

    private MockHttpServletRequest requestRecordingForwardPath(AtomicReference<String> forwardPath) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                forwardPath.set(path);
                return new RequestDispatcher() {
                    @Override
                    public void forward(ServletRequest servletRequest, ServletResponse servletResponse) {
                        ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
                    }

                    @Override
                    public void include(ServletRequest servletRequest, ServletResponse servletResponse) {
                        throw new UnsupportedOperationException("include is not used by this test");
                    }
                };
            }
        };
        request.setContextPath("/carlos");
        return request;
    }

    private void restoreActionContext(ActionContext previousContext) {
        if (previousContext == null) {
            ActionContext.clear();
        } else {
            previousContext.bind();
        }
    }

    @FunctionalInterface
    private interface ForwardBehavior {
        void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException;
    }
}
