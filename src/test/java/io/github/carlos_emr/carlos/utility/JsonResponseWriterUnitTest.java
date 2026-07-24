/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("JsonResponseWriter")
@Tag("unit")
@Tag("utility")
class JsonResponseWriterUnitTest {
    @Test
    @DisplayName("writes UTF-8 JSON with JSON content type")
    void shouldWriteObjectBody_withUtf8JsonContentType() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());

        JsonResponseWriter.write(response, Map.of("message", "Jose 東京"));

        assertThat(response.getContentType()).isEqualTo(JsonResponseWriter.CONTENT_TYPE);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .isEqualTo("{\"message\":\"Jose 東京\"}");
    }

    @Test
    @DisplayName("writes pre-serialized JSON strings without double serialization")
    void shouldWriteStringBody_withoutDoubleSerialization() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        JsonResponseWriter.write(response, "{\"success\":true}");

        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true}");
    }

    @Test
    @DisplayName("uses servlet writer instead of output stream")
    void shouldUseWriter_forServletResponseCompatibility() throws Exception {
        MockHttpServletResponse response = spy(new MockHttpServletResponse());

        JsonResponseWriter.write(response, Map.of("success", true));

        verify(response).getWriter();
        verify(response, never()).getOutputStream();
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true}");
    }
}
