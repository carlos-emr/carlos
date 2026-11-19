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
package io.github.carlos_emr.carlos.app;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.owasp.csrfguard.CsrfGuard;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CsrfGuardScriptInjectionFilter}.
 *
 * @since 2026-05-08
 */
@Tag("unit")
@Tag("security")
@DisplayName("CsrfGuardScriptInjectionFilter")
class CsrfGuardScriptInjectionFilterUnitTest {

    private static final String PROVIDER_PAGE = "<html><head><title>Provider</title></head><body></body></html>";
    private static final String PROVIDER_PAGE_WITH_SCRIPT = "<html><head><title>Provider</title>"
            + "<script src=\"/carlos/csrfguard\"></script>\n</head><body></body></html>";

    private CsrfGuardScriptInjectionFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new CsrfGuardScriptInjectionFilter();
        filter.init(mock(FilterConfig.class));
    }

    @Test
    @DisplayName("should inject CSRFGuard script for extensionless request to forwarded JSP")
    void shouldInjectCsrfGuardScript_forExtensionlessRequestToForwardedJsp() throws Exception {
        // DemographicAdd is a representative extensionless Struts route from struts-demographic.xml.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demographic/DemographicAdd");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.REQUEST);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            MockHttpServletRequest forwardRequest = new MockHttpServletRequest("GET",
                    "/WEB-INF/jsp/demographic/demographicadd.jsp");
            forwardRequest.setContextPath("/carlos");
            forwardRequest.setDispatcherType(DispatcherType.FORWARD);

            filter.doFilter(forwardRequest, servletResponse, (forwardServletRequest, forwardServletResponse) -> {
                forwardServletResponse.setContentType("text/html;charset=UTF-8");
                forwardServletResponse.getWriter().write("<html><head><title>Add</title></head><body></body></html>");
            });
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        String content = response.getContentAsString();
        assertThat(content).contains("<script src=\"/carlos/csrfguard\"></script>\n</head>");
        assertThat(countOccurrences(content, "/csrfguard")).isEqualTo(1);
    }

    // ---- #5937: the FORWARD capture is bounded ----

    /** Writes a page whose body pushes it past the capture bound, in JSP-sized chunks. */
    private static void writeLargePage(jakarta.servlet.ServletResponse response, String head, int bodyChars)
            throws java.io.IOException {
        response.setContentType("text/html;charset=UTF-8");
        var writer = response.getWriter();
        writer.write(head);
        String chunk = "x".repeat(8192);
        for (int written = 0; written < bodyChars; written += chunk.length()) {
            writer.write(chunk);
        }
        writer.write("</body></html>");
    }

    @Test
    @DisplayName("should inject the script into the head and stream a page larger than the capture bound")
    void shouldInjectIntoPrefixAndStream_whenPageExceedsCaptureBound() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/WEB-INF/jsp/big.jsp");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        int body = CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS * 2;
        boolean[] committedWhileWriting = new boolean[1];

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
            writeLargePage(res, "<html><head><title>Big</title></head><body>", body);
            res.flushBuffer();
            committedWhileWriting[0] = res.isCommitted();
            res.setContentLength(7);
        }));

        String content = response.getContentAsString();
        assertThat(content).startsWith("<html><head><title>Big</title><script src=\"/carlos/csrfguard\"></script>\n</head><body>")
                .endsWith("</body></html>");
        assertThat(countOccurrences(content, "/csrfguard")).isEqualTo(1);
        assertThat(content.length()).isGreaterThan(body);
        // Past the bound the wrapper streams: flushBuffer reaches the response and commits it.
        assertThat(committedWhileWriting[0]).isTrue();
        // A length set after the injected prefix was written would be stale; it is not published.
        assertThat(response.getContentLength()).isNotEqualTo(7);
    }

    @Test
    @DisplayName("should not inject twice into a large page that already loads the script")
    void shouldNotInjectTwice_whenLargePageAlreadyLoadsScript() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/WEB-INF/jsp/big.jsp");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse();

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) ->
                writeLargePage(res, "<html><head><script src=\"/carlos/csrfguard\"></script></head><body>",
                        CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS + 1)));

        assertThat(countOccurrences(response.getContentAsString(), "/csrfguard")).isEqualTo(1);
    }

    @Test
    @DisplayName("should inject into a page written in one chunk larger than the capture bound")
    void shouldInjectIntoHead_whenOneWriteCrossesTheBound() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/WEB-INF/jsp/big.jsp");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String page = "<html><head></head><body>" + "y".repeat(CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS) + "</body></html>";

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            res.getWriter().write(page);
        }));

        String content = response.getContentAsString();
        assertThat(content).startsWith("<html><head><script src=\"/carlos/csrfguard\"></script>\n</head><body>");
        assertThat(content.length()).isEqualTo(page.length() + "<script src=\"/carlos/csrfguard\"></script>\n".length());
    }

    @Test
    void shouldStreamCharacterWrites_whenTheContainerRejectsBufferReset() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/WEB-INF/jsp/big.jsp");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public void resetBuffer() {
                throw new IllegalStateException("The container cannot reset this buffer");
            }
        };
        String body = "a".repeat(CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS);
        String followingWrite = "</body></html>";

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            var writer = res.getWriter();
            writer.write('<');
            writer.write("html><head></head><body>".toCharArray());
            writer.write(body.toCharArray());
            writer.write(followingWrite.toCharArray());
            writer.close();
        }));

        assertThat(response.getContentAsString()).isEqualTo(
                "<html><head><script src=\"/carlos/csrfguard\"></script>\n</head><body>" + body + followingWrite);
    }

    /** A forwarded request for one of the large-page streaming cases below. */
    private static MockHttpServletRequest largeForward() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/WEB-INF/jsp/big.jsp");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        return request;
    }

    /** Writes {@code count} characters of filler as JSP-sized char[] chunks, as JspWriter does. */
    private static void writeFillerChunks(Writer writer, int count) throws IOException {
        char[] chunk = "x".repeat(8192).toCharArray();
        for (int written = 0; written < count; written += chunk.length) {
            writer.write(chunk, 0, Math.min(chunk.length, count - written));
        }
    }

    private static final String SCRIPT = "<script src=\"/carlos/csrfguard\"></script>\n";

    @Test
    @DisplayName("should inject before a </body> streamed after the capture bound when the head holds none")
    void shouldInjectIntoStreamedBody_whenThePrefixHasNoClosingTag() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        int filler = CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS + 20_000;

        withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            var writer = res.getWriter();
            writer.write("<html><body>".toCharArray());
            writeFillerChunks(writer, filler);
            writer.write("</body></html>".toCharArray());
        }));

        assertThat(response.getContentAsString())
                .isEqualTo("<html><body>" + "x".repeat(filler) + SCRIPT + "</body></html>");
    }

    @Test
    @DisplayName("should find a closing tag split across two streamed writes")
    void shouldInjectBeforeAClosingTag_whenItIsSplitAcrossStreamedWrites() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        int filler = CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS + 10;

        withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            var writer = res.getWriter();
            writer.write("<html><body>".toCharArray());
            writeFillerChunks(writer, filler);
            writer.write("ab</BO".toCharArray());
            writer.write("DY></html>".toCharArray());
        }));

        assertThat(response.getContentAsString())
                .isEqualTo("<html><body>" + "x".repeat(filler) + "ab" + SCRIPT + "</BODY></html>");
    }

    @Test
    @DisplayName("should stream a large fragment unchanged and warn once when it has no closing tag")
    void shouldStreamFragmentUnchanged_whenNoClosingTagEverArrives() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        int filler = CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS + 100;

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
                res.setContentType("text/html;charset=UTF-8");
                var writer = res.getWriter();
                writer.write("<tr>");
                writeFillerChunks(writer, filler);
                writer.write("</tr>");
            }));

            assertThat(capture.events()).filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement().satisfies(event -> assertThat(event.getMessage().getFormattedMessage())
                            .contains("no </head> or </body>").contains("uri=/WEB-INF/jsp/big.jsp"));
        }
        assertThat(response.getContentAsString()).isEqualTo("<tr>" + "x".repeat(filler) + "</tr>");
    }

    @Test
    @DisplayName("should stream a large response that turned out not to be HTML without injecting")
    void shouldStreamUnchanged_whenTheLargeResponseIsNotHtml() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String page = "<html><head></head><body>" + "x".repeat(CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS)
                + "</body></html>";

        withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
            var writer = res.getWriter();
            writer.write("");
            res.setContentType("text/plain;charset=UTF-8");
            writer.write(page);
        }));

        assertThat(response.getContentAsString()).isEqualTo(page);
    }

    @Test
    @DisplayName("should encode a large streamed page onto the output stream when the writer is unavailable")
    void shouldStreamOntoTheOutputStream_whenTheResponseWriterIsUnavailable() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getOutputStream();
        String body = "é".repeat(CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS);

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
                res.setContentType("text/html;charset=UTF-8");
                var writer = res.getWriter();
                writer.write("<html><head></head><body>");
                writer.write(body);
                writer.write("</body></html>");
            }));

            assertThat(capture.events()).anySatisfy(event -> assertThat(event.getMessage().getFormattedMessage())
                    .contains("encoding it onto the output stream as UTF-8"));
        }
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("<html><head>" + SCRIPT + "</head><body>" + body + "</body></html>");
    }

    @Test
    @DisplayName("should encode a large streamed String onto the output stream without copying it first")
    void shouldEncodeALargeStringInPlace_whenStreamingOntoTheOutputStream() throws Exception {
        com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled(),
                "this JVM cannot report per-thread allocation");
        DiscardingStreamResponse response = new DiscardingStreamResponse();
        String head = "<html><head></head><body>";
        String body = "x".repeat(16 * 1024 * 1024);
        String tail = "</body></html>";
        long[] allocated = new long[1];

        withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            var writer = res.getWriter();
            writer.write(head);
            // Crosses the bound with char[] writes, so the String below is streamed.
            writeFillerChunks(writer, CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS);
            long before = threads.getCurrentThreadAllocatedBytes();
            writer.write(body);
            allocated[0] = threads.getCurrentThreadAllocatedBytes() - before;
            writer.write(tail);
        }));

        assertThat(response.bytesWritten()).isEqualTo((long) head.length() + SCRIPT.length()
                + CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS + body.length() + tail.length());
        // Copying the slice into a char[] alone would allocate 32 MiB; encoding in place needs a few KiB.
        assertThat(allocated[0]).isLessThan(1024L * 1024L);
    }

    @Test
    @DisplayName("should capture the replacement page again after an accepted reset of a streamed page")
    void shouldInjectIntoTheReplacementPage_whenAStreamedPageIsReset() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setBufferSize(8 * CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS);

        withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            var writer = res.getWriter();
            writer.write("<html><head></head><body>");
            writeFillerChunks(writer, CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS);
            res.resetBuffer();
            writer.write("<html><head></head><body>replacement</body></html>");
        }));

        assertThat(response.getContentAsString())
                .isEqualTo("<html><head>" + SCRIPT + "</head><body>replacement</body></html>");
    }

    @Test
    @DisplayName("should release held characters on flush and close while waiting for a closing tag")
    void shouldReleaseHeldCharacters_whenFlushedOrClosedWhileWaiting() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        int filler = CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS + 1;
        String[] beforeClose = new String[1];

        withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            var writer = res.getWriter();
            writer.write("<html><body>");
            writeFillerChunks(writer, filler);
            writer.write("tail");
            writer.flush();
            beforeClose[0] = ((MockHttpServletResponse) response).getContentAsString();
            writer.write("</body>more");
            writer.close();
        }));

        assertThat(beforeClose[0]).endsWith("xtail");
        assertThat(response.getContentAsString())
                .isEqualTo("<html><body>" + "x".repeat(filler) + "tail" + SCRIPT + "</body>more");
    }

    @Test
    @DisplayName("should hold a partial closing tag across a flush so the script lands before the whole tag")
    void shouldInjectBeforeAClosingTag_whenAFlushSplitsIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        int filler = CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS + 1;
        String[] afterFlush = new String[1];

        withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            var writer = res.getWriter();
            writer.write("<html><body>");
            writeFillerChunks(writer, filler);
            writer.write("ab</bo");
            writer.flush();
            afterFlush[0] = ((MockHttpServletResponse) response).getContentAsString();
            writer.write("dy></html>");
        }));

        assertThat(afterFlush[0]).endsWith("xab");
        assertThat(response.getContentAsString())
                .isEqualTo("<html><body>" + "x".repeat(filler) + "ab" + SCRIPT + "</body></html>");
    }

    @Test
    @DisplayName("should write a held partial tag when the streamed response ends without completing it")
    void shouldWriteTheHeldPartialTag_whenTheResponseEndsWithoutCompletingIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        int filler = CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS + 100;

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
                res.setContentType("text/html;charset=UTF-8");
                var writer = res.getWriter();
                writer.write("<tr>");
                writeFillerChunks(writer, filler);
                writer.write("</b");
            }));

            assertThat(capture.events()).filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement().satisfies(event -> assertThat(event.getMessage().getFormattedMessage())
                            .contains("no </head> or </body>"));
        }
        assertThat(response.getContentAsString()).isEqualTo("<tr>" + "x".repeat(filler) + "</b");
    }

    @Test
    @DisplayName("should stream one large write from the caller's array when its closing tag lies past the bound")
    void shouldStreamFromTheCallersArray_whenOneWriteCarriesTheClosingTagPastTheBound() throws Exception {
        ArrayRecordingResponse response = new ArrayRecordingResponse();
        String filler = "x".repeat(CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS + 50_000);
        char[] page = ("<html><body>" + filler + "</body></html>").toCharArray();

        withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            res.getWriter().write(page, 0, page.length);
        }));

        assertThat(response.getContentAsString()).isEqualTo("<html><body>" + filler + SCRIPT + "</body></html>");
        // The part past the bound reached the response straight from the JSP's own array.
        assertThat(response.arrays).anySatisfy(array -> assertThat(array).isSameAs(page));
    }

    @Test
    @DisplayName("should forget a streamed page's held tag, pending script and length rule after a full reset")
    void shouldForgetTheStreamedPage_whenTheResponseIsFullyReset() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setBufferSize(8 * CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS);

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
                res.setContentType("text/html;charset=UTF-8");
                var streamed = res.getWriter();
                streamed.write("<html><body>");
                writeFillerChunks(streamed, CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS);
                streamed.write("</bo");
                res.reset();
                res.setContentType("text/plain;charset=UTF-8");
                res.setContentLength(5);
                res.getWriter().write("plain");
            }));

            assertThat(capture.events()).noneSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        }
        assertThat(response.getContentAsString()).isEqualTo("plain");
        assertThat(response.getContentLength()).isEqualTo(5);
    }

    @Test
    @DisplayName("should stream a large page without a buffer reset when the response is already committed")
    void shouldStreamWithoutReset_whenTheResponseIsAlreadyCommitted() throws Exception {
        MockHttpServletResponse response = spy(new MockHttpServletResponse());
        response.setCommitted(true);
        String page = "<html><head></head><body>" + "x".repeat(CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS)
                + "</body></html>";

        withEnabledCsrfGuard(() -> filter.doFilter(largeForward(), response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            res.getWriter().write(page);
        }));

        verify(response, never()).resetBuffer();
        assertThat(response.getContentAsString()).isEqualTo(page.replace("</head>", SCRIPT + "</head>"));
    }

    @Test
    @DisplayName("should keep replaying a page within the capture bound as one injected document")
    void shouldReplayCapturedPage_whenWithinTheBound() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/WEB-INF/jsp/small.jsp");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] committedWhileWriting = new boolean[1];

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
            writeLargePage(res, "<html><head></head><body>", CsrfGuardScriptInjectionFilter.MAX_CAPTURE_CHARS / 2);
            res.flushBuffer();
            committedWhileWriting[0] = res.isCommitted();
            res.getWriter().close();
        }));

        assertThat(committedWhileWriting[0]).isFalse();
        assertThat(countOccurrences(response.getContentAsString(), "/csrfguard")).isEqualTo(1);
    }

    @Test
    @DisplayName("should pass through unchanged when CSRFGuard is disabled")
    void shouldPassThroughUnchanged_whenCsrfGuardDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "<html><head><title>Provider</title></head><body></body></html>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        withDisabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should skip AJAX request dispatch when request mapping is enabled")
    void shouldSkipAjaxRequestDispatch_whenRequestMappingEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clinical/AjaxEndpoint");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.REQUEST);
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should skip injection for the server-side eForm PDF renderer route")
    void shouldSkipInjection_forEformPdfRendererRoute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/EFormViewForPdfGenerationServlet");
        request.setServletPath("/EFormViewForPdfGenerationServlet");
        request.setDispatcherType(DispatcherType.REQUEST);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // The renderer output must be captured byte-for-byte; the filter must pass through without
        // wrapping/injecting even while CsrfGuard is enabled.
        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        verify(chain).doFilter(request, response);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should fail closed when CsrfGuard cannot initialize")
    void shouldFailClosed_whenCsrfGuardCannotInitialize() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/provider/providercontrol;jsessionid=secret-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class);
             LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            csrfGuardMock.when(CsrfGuard::getInstance).thenThrow(new IllegalStateException("csrf unavailable"));

            filter.doFilter(request, response, chain);

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("uri=/carlos/provider/providercontrol")
                        .doesNotContain("jsessionid")
                        .doesNotContain("secret-session");
            });
        }

        assertThat(response.getStatus()).isEqualTo(503);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should not inject duplicate CSRFGuard script")
    void shouldNotInjectDuplicateCsrfGuardScript_whenScriptAlreadyExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/encounter/IncomingEncounter");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><head><script src=\"/carlos/csrfguard\"></script></head>"
                    + "<body></body></html>");
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        String content = response.getContentAsString();
        assertThat(countOccurrences(content, "/csrfguard")).isEqualTo(1);
    }

    @Test
    @DisplayName("should warn when writer fallback uses output stream")
    void shouldWarn_whenWriterFallbackUsesOutputStream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/provider/providercontrol;jsessionid=secret-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getOutputStream();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(PROVIDER_PAGE);
        };

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

            assertThat(response.getContentAsString()).contains("/carlos/csrfguard");
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("falling back to output stream")
                        .contains("uri=/carlos/provider/providercontrol")
                        .contains("contentType=text/html;charset=UTF-8")
                        .contains("committed=false")
                        .doesNotContain("jsessionid")
                        .doesNotContain("secret-session");
            });
        }
    }

    @ParameterizedTest
    @MethodSource("replayCharsets")
    void shouldEncodeTheCapturedReplay_whenAForwardMustUseTheOutputStream(
            String declaredCharset, Charset expectedCharset) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public String getCharacterEncoding() {
                return declaredCharset;
            }
        };
        response.getOutputStream();
        String body = "<html><head><title>Québec</title></head><body>café</body></html>";
        String expected = body.replace("</head>", "<script src=\"/carlos/csrfguard\"></script>\n</head>");

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
            res.setContentType("text/html");
            res.getWriter().write(body);
        }));

        byte[] expectedBytes = expected.getBytes(expectedCharset);
        assertThat(response.getContentAsByteArray()).isEqualTo(expectedBytes);
        assertThat(response.getContentLength()).isEqualTo(expectedBytes.length);
    }

    private static Stream<Arguments> replayCharsets() {
        return Stream.of(
                Arguments.of(null, StandardCharsets.UTF_8),
                Arguments.of("", StandardCharsets.UTF_8),
                Arguments.of("not-a-supported-charset", StandardCharsets.UTF_8),
                Arguments.of("bad charset!", StandardCharsets.UTF_8),
                Arguments.of("UTF-8", StandardCharsets.UTF_8),
                Arguments.of("ISO-8859-1", StandardCharsets.ISO_8859_1),
                Arguments.of("UTF-16LE", StandardCharsets.UTF_16LE));
    }

    @Test
    @DisplayName("should pass through output-stream responses")
    void shouldPassThrough_whenResponseUsesOutputStream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/OscarChartPrint");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] body = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/pdf");
            servletResponse.getOutputStream().write(body);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should pass through redirect responses without injection")
    void shouldPassThrough_whenDownstreamRedirects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendRedirect("/carlos/index");

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/index");
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should pass through error responses without injection")
    void shouldPassThrough_whenDownstreamSendsError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).sendError(403);

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should not inject script into HTML fragments")
    void shouldNotInjectScript_intoHtmlFragments() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fragment");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String fragment = "<tr><td>Partial row</td></tr>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(fragment);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).isEqualTo(fragment);
    }

    @Test
    @DisplayName("should wrap forward dispatch even when AJAX header is present")
    void shouldWrapForwardDispatch_whenAjaxHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/WEB-INF/jsp/provider/providercontrol.jsp");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write("<html><head><title>Provider</title></head><body></body></html>");
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).contains("/carlos/csrfguard");
    }

    @Test
    @DisplayName("should pass through non-HTML writer responses when content type is set before writer")
    void shouldPassThrough_whenNonHtmlContentTypePrecedesWriter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clinical/JsonEndpoint");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "{\"status\":\"ok\"}";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should flush non-HTML passthrough writer response")
    void shouldFlushNonHtmlPassthroughWriterResponse_whenContentTypePrecedesWriter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/WEB-INF/jsp/provider/schedulePage.js.jsp");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        BufferingWriterResponse response = new BufferingWriterResponse();
        String body = "function schedulePage() { return 'ready'; }\n";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/javascript;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
    }

    @Test
    @DisplayName("should pass through non-HTML writer responses when content type follows writer")
    void shouldPassThrough_whenNonHtmlContentTypeFollowsWriter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clinical/JsonEndpoint");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "{\"status\":\"ok\"}";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
            PrintWriter writer = servletResponse.getWriter();
            servletResponse.setContentType("application/json;charset=UTF-8");
            writer.write(body);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getContentAsString()).doesNotContain("/csrfguard");
        assertThat(response.getContentLength()).isEqualTo(body.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("should drop stale content length header when injecting script")
    void shouldDropStaleContentLengthHeader_whenInjectingScript() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "<html><head><title>Provider</title></head><body></body></html>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            ((HttpServletResponse) servletResponse).setHeader("Content-Length",
                    String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

        assertThat(response.getContentAsString()).contains("/carlos/csrfguard");
        assertThat(response.getHeader("Content-Length")).isNull();
    }

    @Test
    @DisplayName("should warn when non HTML content type contains full HTML")
    void shouldWarn_whenNonHtmlContentTypeContainsFullHtml() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clinical/JsonEndpoint");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/clinical/JsonEndpoint;jsessionid=secret-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "<!DOCTYPE html><html><head><title>Wrong type</title></head><body></body></html>";

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.getWriter().write(body);
        };

        // Production emits this warning once per JVM. Other response tests may already have
        // consumed it; isolate this assertion and restore the original latch afterwards.
        var warned = (java.util.concurrent.atomic.AtomicBoolean) org.springframework.test.util.ReflectionTestUtils
                .getField(CsrfGuardScriptInjectionFilter.class, "HTML_LOOKING_PASSTHROUGH_WARNED");
        boolean previouslyWarned = warned.getAndSet(false);
        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("non-HTML Content-Type")
                        .contains("/carlos/clinical/JsonEndpoint")
                        .doesNotContain("jsessionid")
                        .doesNotContain("secret-session");
            });
        } finally {
            warned.set(previouslyWarned);
        }
        assertThat(response.getContentAsString()).isEqualTo(body);
    }

    // These tests use a response double that accepts writes after commit. A suspended Tomcat
    // response discards them, so they prove what the filter attempts and logs, not what reaches
    // the browser. The Playwright login check against Tomcat covers only the normal, uncommitted
    // path; neither committed branch has been exercised on Tomcat.
    @Test
    @DisplayName("should replay captured HTML without reset when response is committed")
    void shouldReplayCapturedHtml_withoutResetWhenResponseIsCommitted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/provider/providercontrol;jsessionid=secret-session");
        CommittedResetTrackingResponse response = new CommittedResetTrackingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(PROVIDER_PAGE);
        };

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

            assertThat(response.getContentAsString()).isEqualTo(PROVIDER_PAGE_WITH_SCRIPT);
            assertThat(response.getResetBufferCalls()).isZero();
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrown()).isNull();
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("response already committed before CSRF-adjusted replay")
                        .contains("writing captured content without buffer reset")
                        .contains("may discard it")
                        .contains("status=200")
                        .contains("uri=/carlos/provider/providercontrol")
                        .doesNotContain("jsessionid")
                        .doesNotContain("secret-session");
            });
        }
    }

    @Test
    @DisplayName("should replay captured HTML without stack trace when an uncommitted response rejects the reset")
    void shouldReplayCapturedHtml_withoutStackTraceWhenUncommittedResponseRejectsReset() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/provider/providercontrol;jsessionid=secret-session");
        MockHttpServletResponse response = new ResetBufferFailingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(PROVIDER_PAGE);
        };

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

            assertThat(response.getContentAsString()).isEqualTo(PROVIDER_PAGE_WITH_SCRIPT);
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrown()).isNull();
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("resetBuffer() rejected although the response and the innermost response "
                                + "reported uncommitted")
                        .contains("writing captured content without buffer reset")
                        .contains("innermostCommitted=false")
                        .contains("reason=reset rejected")
                        .contains("uri=/carlos/provider/providercontrol")
                        .doesNotContain("jsessionid")
                        .doesNotContain("secret-session");
            });
        }
    }

    @Test
    @DisplayName("should skip the reset when a capture wrapper hides a committed response")
    void shouldSkipReset_whenCaptureWrapperHidesCommittedResponse() throws Exception {
        // ResponseSanitizationFilter's CapturingResponseWrapper reports uncommitted in writer-capture
        // mode and delegates resetBuffer() to the response beneath it, which rejects it once
        // committed. The filter must see the innermost commit and not attempt the reset.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/WEB-INF/jsp/provider/providercontrol.jsp");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/WEB-INF/jsp/provider/providercontrol.jsp;jsessionid=secret-session");
        request.setDispatcherType(DispatcherType.FORWARD);
        CommittedResetTrackingResponse committedResponse = new CommittedResetTrackingResponse();
        CommitHidingCaptureWrapper response = new CommitHidingCaptureWrapper(committedResponse);

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/html;charset=UTF-8");
            servletResponse.getWriter().write(PROVIDER_PAGE);
        };

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

            assertThat(committedResponse.getContentAsString()).isEqualTo(PROVIDER_PAGE_WITH_SCRIPT);
            assertThat(response.getResetBufferCalls()).isZero();
            assertThat(committedResponse.getResetBufferCalls()).isZero();
            assertThat(capture.events())
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getThrown()).isNull();
                        assertThat(event.getMessage().getFormattedMessage())
                                .contains("innermost response already committed beneath a wrapper")
                                .contains("writing captured content without buffer reset")
                                .contains("committed=false")
                                .contains("innermostCommitted=true")
                                .contains("uri=/carlos/WEB-INF/jsp/provider/providercontrol.jsp")
                                .doesNotContain("jsessionid")
                                .doesNotContain("secret-session");
                    });
        }
    }

    @Test
    @DisplayName("should find the commit beneath every wrapper layer")
    void shouldReportInnermostCommit_throughNestedWrappers() {
        MockHttpServletResponse committed = new MockHttpServletResponse();
        committed.setCommitted(true);
        MockHttpServletResponse uncommitted = new MockHttpServletResponse();

        assertThat(CsrfGuardScriptInjectionFilter.isInnermostResponseCommitted(new CommitHidingCaptureWrapper(
                new HttpServletResponseWrapper(new CommitHidingCaptureWrapper(committed))))).isTrue();
        assertThat(CsrfGuardScriptInjectionFilter.isInnermostResponseCommitted(new CommitHidingCaptureWrapper(
                new HttpServletResponseWrapper(uncommitted)))).isFalse();
        assertThat(CsrfGuardScriptInjectionFilter.isInnermostResponseCommitted(committed)).isTrue();
    }

    @Test
    @DisplayName("should stop unwrapping at the depth bound when a wrapper returns itself")
    void shouldStopUnwrapping_whenWrapperReturnsItself() {
        HttpServletResponseWrapper selfReferencing = new HttpServletResponseWrapper(new MockHttpServletResponse()) {
            @Override
            public ServletResponse getResponse() {
                return this;
            }

            @Override
            public boolean isCommitted() {
                return true;
            }
        };

        assertThat(CsrfGuardScriptInjectionFilter.isInnermostResponseCommitted(selfReferencing)).isTrue();
    }

    @Test
    @DisplayName("should inject once when nested forwards meet a committed response")
    void shouldInjectOnceWithoutStackTraces_whenNestedForwardMeetsCommittedResponse() throws Exception {
        // The #3434 shape through the real wrapper, a forward inside a forward: the container has
        // committed the response and the outer CaptureResponseWrapper still reports uncommitted in
        // writer-capture mode. The inner replay must see the innermost commit and skip the reset.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/provider/providercontrol");
        request.setDispatcherType(DispatcherType.FORWARD);
        CommittedResetTrackingResponse response = new CommittedResetTrackingResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.getWriter();
            MockHttpServletRequest forwardRequest = new MockHttpServletRequest("GET",
                    "/WEB-INF/jsp/provider/providercontrol.jsp");
            forwardRequest.setContextPath("/carlos");
            forwardRequest.setDispatcherType(DispatcherType.FORWARD);

            filter.doFilter(forwardRequest, servletResponse, (forwardServletRequest, forwardServletResponse) -> {
                forwardServletResponse.setContentType("text/html;charset=UTF-8");
                forwardServletResponse.getWriter().write(PROVIDER_PAGE);
            });
        };

        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, chain));

            String content = response.getContentAsString();
            assertThat(content).isEqualTo(PROVIDER_PAGE_WITH_SCRIPT);
            assertThat(countOccurrences(content, "/csrfguard")).isEqualTo(1);
            assertThat(response.getResetBufferCalls()).isZero();

            assertThat(capture.events())
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .allSatisfy(event -> assertThat(event.getThrown()).isNull());
            assertThat(capture.events())
                    .filteredOn(event -> event.getMessage().getFormattedMessage()
                            .contains("innermost response already committed beneath a wrapper"))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getMessage().getFormattedMessage())
                                .contains("uri=/WEB-INF/jsp/provider/providercontrol.jsp");
                    });
            assertThat(capture.events())
                    .filteredOn(event -> event.getMessage().getFormattedMessage()
                            .contains("response already committed before CSRF-adjusted replay"))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getMessage().getFormattedMessage())
                                .contains("uri=/carlos/provider/providercontrol,");
                    });
        }
    }

    @Test
    void shouldDiscardCapturedPrefix_whenNestedForwardResetsTheBuffer() throws Exception {
        for (boolean fullReset : new boolean[] {false, true}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
            request.setContextPath("/carlos");
            request.setDispatcherType(DispatcherType.FORWARD);
            MockHttpServletResponse response = new MockHttpServletResponse();
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
                res.getWriter().write("DISCARDED PREFIX");
                if (fullReset) {
                    res.reset();
                } else {
                    res.resetBuffer();
                }
                res.setContentType("text/html;charset=UTF-8");
                res.getWriter().write(PROVIDER_PAGE);
            }));
            assertThat(response.getContentAsString()).isEqualTo(PROVIDER_PAGE_WITH_SCRIPT);
        }
    }

    @Test
    void shouldKeepCapturedBody_whenUnderlyingResetIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            res.getWriter().write(PROVIDER_PAGE);
            response.setCommitted(true);
            org.assertj.core.api.Assertions.assertThatThrownBy(res::resetBuffer)
                    .isInstanceOf(IllegalStateException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(res::reset)
                    .isInstanceOf(IllegalStateException.class);
        }));
        assertThat(response.getContentAsString()).isEqualTo(PROVIDER_PAGE_WITH_SCRIPT);
    }

    @Test
    void shouldDropPendingLength_onBufferResetAndOnFullReset() throws Exception {
        for (boolean fullReset : new boolean[] {false, true}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
            request.setContextPath("/carlos");
            request.setDispatcherType(DispatcherType.FORWARD);
            MockHttpServletResponse response = new MockHttpServletResponse();
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
                res.setContentLength(4);
                if (fullReset) {
                    res.reset();
                } else {
                    res.resetBuffer();
                }
                res.setContentType("application/octet-stream");
                res.getOutputStream().write(new byte[] {0, 1, 2, 3});
            }));
            assertThat(response.getHeader("Content-Length")).as("fullReset=%s", fullReset).isNull();
            assertThat(response.getContentAsByteArray()).containsExactly(0, 1, 2, 3);
        }
    }

    @Test
    void shouldRetractPublishedLength_whenBufferResetDiscardsThePassthroughBody() throws Exception {
        for (boolean stream : new boolean[] {false, true}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
            request.setContextPath("/carlos");
            request.setDispatcherType(DispatcherType.FORWARD);
            MockHttpServletResponse response = spy(new MockHttpServletResponse());
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
                res.setContentType(stream ? "application/octet-stream" : "text/plain;charset=UTF-8");
                if (stream) {
                    res.getOutputStream().write(new byte[] {0, 1, 2, 3});
                } else {
                    res.getWriter().write("abcd");
                }
                res.setContentLength(4);
                assertThat(response.getContentLength()).as("passthrough length published, stream=%s", stream)
                        .isEqualTo(4);
                res.resetBuffer();
                // The published length described the discarded body. Spring's mock ignores null
                // values, so the removal call itself is asserted.
                verify(response).setHeader("Content-Length", null);
            }));
        }
    }

    @Test
    void shouldKeepPublishedLength_whenPassthroughBufferResetIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = spy(new MockHttpServletResponse());
        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
            res.setContentType("application/octet-stream");
            res.getOutputStream().write(new byte[] {0, 1, 2, 3});
            res.setContentLength(4);
            response.setCommitted(true);
            org.assertj.core.api.Assertions.assertThatThrownBy(res::resetBuffer)
                    .isInstanceOf(IllegalStateException.class);
        }));
        verify(response, never()).setHeader("Content-Length", null);
        assertThat(response.getContentLength()).isEqualTo(4);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void shouldPreserveStreamFraming_whenLengthHeadersAreReplacedOrAdded(boolean add) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setHeader("X-Mode", "first");
            http.addHeader("X-Mode", "second");
            http.setIntHeader("X-Limit", 1);
            http.addIntHeader("X-Limit", 2);
            http.setHeader("Content-Length", "2");
            if (add) http.addHeader("cOnTeNt-LeNgTh", "4");
            else http.setHeader("cOnTeNt-LeNgTh", "4");
            http.addHeader("Content-Length", null);
            res.setContentType("application/octet-stream");
            res.getOutputStream().write(new byte[] {0, 1, 2, 3});
        }));
        assertThat(response.getContentAsByteArray()).containsExactly(0, 1, 2, 3);
        assertThat(response.getHeaders("Content-Length")).containsExactly("4");
        assertThat(response.getHeaders("X-Mode")).containsExactly("first", "second");
        assertThat(response.getHeaders("X-Limit")).containsExactly("1", "2");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void shouldPreserveStreamFraming_whenIntegerLengthHeadersAreUsed(boolean add) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            if (add) http.addIntHeader("Content-Length", 4);
            else http.setIntHeader("Content-Length", 4);
            res.setContentType("application/octet-stream");
            res.getOutputStream().write(new byte[] {0, 1, 2, 3});
        }));
        assertThat(response.getContentAsByteArray()).containsExactly(0, 1, 2, 3);
        assertThat(response.getHeaders("Content-Length")).containsExactly("4");
    }

    @Test
    void shouldKeepAnUnframedStream_whenItsLengthIsRemovedAndAMalformedValueIsAdded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (LogCapture capture = LogCapture.forLogger(CsrfGuardScriptInjectionFilter.class)) {
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
                HttpServletResponse http = (HttpServletResponse) res;
                http.setHeader("Content-Length", "4");
                http.setHeader("Content-Length", null);
                http.addHeader("Content-Length", "invalid");
                res.setContentType("application/octet-stream");
                res.getOutputStream().write(new byte[] {0, 1, 2, 3});
            }));
            assertThat(response.getContentAsByteArray()).containsExactly(0, 1, 2, 3);
            assertThat(response.getHeader("Content-Length")).isNull();
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage()).contains("Ignoring malformed Content-Length");
            });
        }
    }

    @Test
    void shouldAllowChannelReselection_afterFullReset() throws Exception {
        for (boolean writerFirst : new boolean[] {false, true}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/forward");
            request.setContextPath("/carlos");
            request.setDispatcherType(DispatcherType.FORWARD);
            MockHttpServletResponse response = new MockHttpServletResponse();
            withEnabledCsrfGuard(() -> filter.doFilter(request, response, (req, res) -> {
                if (writerFirst) {
                    res.getWriter().write("discarded");
                } else {
                    res.getOutputStream().write(new byte[] {9, 9});
                }
                res.reset();
                if (writerFirst) {
                    res.setContentType("application/octet-stream");
                    res.getOutputStream().write(new byte[] {0, 1, 2, 3});
                } else {
                    res.setContentType("text/html;charset=UTF-8");
                    res.getWriter().write(PROVIDER_PAGE);
                }
            }));
            if (writerFirst) {
                assertThat(response.getContentAsByteArray()).containsExactly(0, 1, 2, 3);
            } else {
                assertThat(response.getContentAsString()).isEqualTo(PROVIDER_PAGE_WITH_SCRIPT);
            }
        }
    }

    private void withEnabledCsrfGuard(Executable executable) throws Exception {
        CsrfGuard csrfGuard = mock(CsrfGuard.class);
        when(csrfGuard.isEnabled()).thenReturn(true);
        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class)) {
            csrfGuardMock.when(CsrfGuard::getInstance).thenReturn(csrfGuard);
            try {
                executable.execute();
            } catch (Throwable t) {
                if (t instanceof Exception e) {
                    throw e;
                }
                throw new AssertionError(t);
            }
        }
    }

    private void withDisabledCsrfGuard(Executable executable) throws Exception {
        CsrfGuard csrfGuard = mock(CsrfGuard.class);
        when(csrfGuard.isEnabled()).thenReturn(false);
        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class)) {
            csrfGuardMock.when(CsrfGuard::getInstance).thenReturn(csrfGuard);
            try {
                executable.execute();
            } catch (Throwable t) {
                if (t instanceof Exception e) {
                    throw e;
                }
                throw new AssertionError(t);
            }
        }
    }

    private int countOccurrences(String content, String needle) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    /**
     * Stands in for ResponseSanitizationFilter's CapturingResponseWrapper in writer-capture mode:
     * reports uncommitted whatever the response beneath it says, and delegates resetBuffer().
     */
    private static class CommitHidingCaptureWrapper extends HttpServletResponseWrapper {

        private int resetBufferCalls;

        CommitHidingCaptureWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void resetBuffer() {
            resetBufferCalls++;
            super.resetBuffer();
        }

        int getResetBufferCalls() {
            return resetBufferCalls;
        }
    }

    private static class ResetBufferFailingResponse extends MockHttpServletResponse {
        @Override
        public void resetBuffer() {
            throw new IllegalStateException("reset rejected");
        }
    }

    private static class CommittedResetTrackingResponse extends MockHttpServletResponse {

        private int resetBufferCalls;

        CommittedResetTrackingResponse() {
            setCommitted(true);
        }

        @Override
        public void resetBuffer() {
            resetBufferCalls++;
            super.resetBuffer();
        }

        int getResetBufferCalls() {
            return resetBufferCalls;
        }
    }

    /** An output stream that only counts bytes, with the writer unavailable as after getOutputStream(). */
    private static class DiscardingStreamResponse extends MockHttpServletResponse {

        private long bytes;
        private final ServletOutputStream countingStream = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // Blocking writes only.
            }

            @Override
            public void write(int b) {
                bytes++;
            }

            @Override
            public void write(byte[] b, int off, int len) {
                bytes += len;
            }
        };

        @Override
        public ServletOutputStream getOutputStream() {
            return countingStream;
        }

        @Override
        public PrintWriter getWriter() {
            throw new IllegalStateException("getOutputStream() has already been called for this response");
        }

        long bytesWritten() {
            return bytes;
        }
    }

    /** Records the arrays its writer receives, so a test can tell a streamed write was not copied. */
    private static class ArrayRecordingResponse extends MockHttpServletResponse {

        private final StringBuilder content = new StringBuilder();
        private final List<char[]> arrays = new ArrayList<>();
        private final PrintWriter recordingWriter = new PrintWriter(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) {
                arrays.add(cbuf);
                content.append(cbuf, off, len);
            }

            @Override
            public void write(String str, int off, int len) {
                content.append(str, off, off + len);
            }

            @Override
            public void flush() {
                // Nothing is buffered.
            }

            @Override
            public void close() {
                // Nothing to release.
            }
        });

        @Override
        public PrintWriter getWriter() {
            return recordingWriter;
        }

        @Override
        public String getContentAsString() {
            return content.toString();
        }
    }

    private static class BufferingWriterResponse extends MockHttpServletResponse {

        private final StringBuilder pending = new StringBuilder();
        private final StringBuilder flushed = new StringBuilder();
        private PrintWriter bufferingWriter;

        @Override
        public PrintWriter getWriter() {
            if (bufferingWriter == null) {
                bufferingWriter = new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) {
                        pending.append(cbuf, off, len);
                    }

                    @Override
                    public void flush() {
                        flushed.append(pending);
                        pending.setLength(0);
                    }

                    @Override
                    public void close() {
                        flush();
                    }
                });
            }
            return bufferingWriter;
        }

        @Override
        public String getContentAsString() {
            return flushed.toString();
        }
    }
}
