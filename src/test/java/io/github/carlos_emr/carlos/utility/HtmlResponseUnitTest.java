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
package io.github.carlos_emr.carlos.utility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HtmlResponse}.
 */
@DisplayName("HtmlResponse Unit Tests")
@Tag("unit")
class HtmlResponseUnitTest {

    @Test
    @DisplayName("should resolve declared charset when content type includes charset")
    void shouldResolveDeclaredCharset_whenContentTypeIncludesCharset() {
        assertThat(HtmlResponse.resolveCharset("text/html; charset=ISO-8859-1"))
                .isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("should resolve quoted charset when content type quotes charset")
    void shouldResolveQuotedCharset_whenContentTypeQuotesCharset() {
        assertThat(HtmlResponse.resolveCharset("text/html; charset=\"windows-1252\""))
                .isEqualTo(Charset.forName("windows-1252"));
    }

    @Test
    @DisplayName("should default to UTF-8 when content type omits charset")
    void shouldDefaultToUtf8_whenContentTypeOmitsCharset() {
        assertThat(HtmlResponse.resolveCharset("text/html"))
                .isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should ignore quoted semicolons when resolving charset")
    void shouldIgnoreQuotedSemicolons_whenResolvingCharset() {
        assertThat(HtmlResponse.resolveCharset("text/html; title=\"a;b\"; charset=ISO-8859-1"))
                .isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("should write byte content with declared charset")
    void shouldWriteByteContent_withDeclaredCharset() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] html = "<html><body>café</body></html>".getBytes(StandardCharsets.ISO_8859_1);

        HtmlResponse.writeStoredHtml(response, "text/html; charset=ISO-8859-1", html);

        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.ISO_8859_1.name());
        assertThat(response.getContentAsString(StandardCharsets.ISO_8859_1))
                .isEqualTo("<html><body>café</body></html>");
    }

    @Test
    @DisplayName("should write stream content with declared charset")
    void shouldWriteStreamContent_withDeclaredCharset() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] html = "<html><body>déjà vu</body></html>".getBytes(StandardCharsets.ISO_8859_1);

        HtmlResponse.writeStoredHtml(
                response,
                "text/html; charset=ISO-8859-1",
                new ByteArrayInputStream(html));

        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.ISO_8859_1.name());
        assertThat(response.getContentAsString(StandardCharsets.ISO_8859_1))
                .isEqualTo("<html><body>déjà vu</body></html>");
    }

    @Test
    @DisplayName("should leave stream open when writing stream content")
    void shouldLeaveStreamOpen_whenWritingStreamContent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        CloseTrackingInputStream html = new CloseTrackingInputStream(
                "<html><body>owned by caller</body></html>".getBytes(StandardCharsets.UTF_8));

        HtmlResponse.writeStoredHtml(response, "text/html; charset=UTF-8", html);

        assertThat(html.isClosed()).isFalse();
        html.close();
        assertThat(html.isClosed()).isTrue();
    }

    @Test
    @DisplayName("should replace invalid charset header with fallback charset")
    void shouldReplaceInvalidCharsetHeader_withFallbackCharset() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] html = "<html><body>fallback</body></html>".getBytes(StandardCharsets.UTF_8);

        HtmlResponse.writeStoredHtml(response, "text/html; charset=not-a-charset", html);

        assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString()).isEqualTo("<html><body>fallback</body></html>");
    }

    @Test
    @DisplayName("should replace malformed charset header with fallback charset")
    void shouldReplaceMalformedCharsetHeader_withFallbackCharset() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        HtmlResponse.writeStoredHtml(response, "text/html; charset=bad charset", "<html></html>");

        assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
    }

    @Test
    @DisplayName("should default content type when content type is null")
    void shouldDefaultContentType_whenContentTypeIsNull() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String result = HtmlResponse.of(null, "<html><body>default</body></html>").writeTo(response);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(response.getContentAsString()).isEqualTo("<html><body>default</body></html>");
    }

    @Test
    @DisplayName("should preserve writer-backed response when body is null")
    void shouldPreserveWriterBackedResponse_whenBodyIsNull() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String result = HtmlResponse.of("text/html", (byte[]) null).writeTo(response);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(response.getContentAsString()).isEmpty();
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream(byte[] buf) {
            super(buf);
        }

        private boolean isClosed() {
            return closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
