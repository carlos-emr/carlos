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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HtmlResponse}.
 *
 * @since 2026-05-20
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
}
