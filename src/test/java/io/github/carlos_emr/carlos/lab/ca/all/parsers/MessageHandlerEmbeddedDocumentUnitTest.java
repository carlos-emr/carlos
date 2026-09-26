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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MessageHandler#isOBXEmbeddedDocument(int, int)}: embedded documents are
 * detected by the OBX value type, never by the shape of the result text (#3946).
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
class MessageHandlerEmbeddedDocumentUnitTest {

    @Test
    @DisplayName("should detect ED by value type, ignoring surrounding whitespace")
    void shouldDetectEmbeddedDocument_byOBXValueType() {
        MessageHandler handler = mock(MessageHandler.class, CALLS_REAL_METHODS);
        when(handler.getOBXValueType(0, 0)).thenReturn("ED");
        when(handler.getOBXValueType(0, 1)).thenReturn(" ED ");
        when(handler.getOBXValueType(0, 2)).thenReturn("FT");
        when(handler.getOBXValueType(0, 3)).thenReturn(null);
        when(handler.getOBXValueType(0, 4)).thenReturn("NA");

        assertThat(handler.isOBXEmbeddedDocument(0, 0)).isTrue();
        assertThat(handler.isOBXEmbeddedDocument(0, 1)).isTrue();
        assertThat(handler.isOBXEmbeddedDocument(0, 2)).isFalse();
        assertThat(handler.isOBXEmbeddedDocument(0, 3)).isFalse();
        assertThat(handler.isOBXEmbeddedDocument(0, 4)).isFalse();
    }

    @Test
    @DisplayName("should flag only the ED segment of a PATHL7 message that mixes results and a PDF")
    void shouldFlagOnlyEdSegment_whenMessageMixesResultsAndDocument() throws Exception {
        PATHL7Handler handler = new PATHL7Handler();
        handler.init(PathL7EmbeddedDocumentMessage.message());

        assertThat(handler.getOBRCount()).isEqualTo(2);
        assertThat(handler.isOBXEmbeddedDocument(0, 0)).isFalse();
        // Long, base64-alphabet-only free text is still a result.
        assertThat(handler.isOBXEmbeddedDocument(0, 1)).isFalse();
        assertThat(handler.isOBXEmbeddedDocument(1, 0)).isTrue();
        assertThat(Base64.getDecoder().decode(handler.getOBXResult(1, 0)))
                .isEqualTo(PathL7EmbeddedDocumentMessage.PDF);
        assertThat(new String(PathL7EmbeddedDocumentMessage.PDF, StandardCharsets.US_ASCII)).startsWith("%PDF");
    }
}
