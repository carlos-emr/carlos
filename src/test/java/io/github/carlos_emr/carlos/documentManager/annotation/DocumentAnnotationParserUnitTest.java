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
package io.github.carlos_emr.carlos.documentManager.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the trust boundary for annotation input.
 *
 * <p>The parser is the only thing standing between a browser payload and PDFBox drawing
 * on a clinical document, so every documented limit is asserted here rather than trusted
 * to the viewer.
 */
@Tag("unit")
@Tag("document")
@DisplayName("DocumentAnnotationParser")
class DocumentAnnotationParserUnitTest {

    private static final int PAGES = 5;

    private final DocumentAnnotationParser parser = new DocumentAnnotationParser();

    @Nested
    @DisplayName("Accepts well-formed input")
    class Accepts {

        @Test
        @DisplayName("should parse every supported type when the payload is valid")
        void shouldParseEveryType_whenPayloadValid() {
            String json = """
                    {"annotations":[
                      {"type":"highlight","page":1,"x":0.1,"y":0.2,"w":0.3,"h":0.02,"color":"yellow"},
                      {"type":"text","page":2,"x":0.5,"y":0.1,"w":0.3,"h":0.03,"text":"call re dosage","fontSize":12},
                      {"type":"ink","page":3,"points":[[0.1,0.1],[0.2,0.2]],"strokeWidth":3,"color":"blue"},
                      {"type":"signature","page":4,"x":0.5,"y":0.8,"w":0.3,"h":0.06},
                      {"type":"date","page":5,"x":0.1,"y":0.9,"w":0.2,"h":0.02,"text":"2026-09-06"}
                    ]}""";

            List<DocumentAnnotationDto> parsed = parser.parse(json, PAGES);

            assertThat(parsed).hasSize(5);
            assertThat(parsed).extracting(DocumentAnnotationDto::getType)
                    .containsExactly(DocumentAnnotationDto.Type.HIGHLIGHT,
                            DocumentAnnotationDto.Type.TEXT,
                            DocumentAnnotationDto.Type.INK,
                            DocumentAnnotationDto.Type.SIGNATURE,
                            DocumentAnnotationDto.Type.DATE);
            assertThat(parsed.get(2).getPoints()).hasSize(2);
            assertThat(parsed.get(1).getFontSize()).isEqualTo(12d);
        }

        @Test
        @DisplayName("should default colour and stroke width when omitted")
        void shouldApplyDefaults_whenOptionalFieldsOmitted() {
            String json = """
                    {"annotations":[{"type":"ink","page":1,"points":[[0.1,0.1],[0.2,0.2]]}]}""";

            DocumentAnnotationDto ink = parser.parse(json, PAGES).get(0);

            assertThat(ink.getColor()).isEqualTo("black");
            assertThat(ink.getStrokeWidth()).isEqualTo(2d);
        }

        @Test
        @DisplayName("should return an empty list when the annotations array is empty")
        void shouldReturnEmptyList_whenNoAnnotations() {
            assertThat(parser.parse("{\"annotations\":[]}", PAGES)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Rejects malformed or out-of-range input")
    class Rejects {

        @Test
        @DisplayName("should throw when the type is unrecognised")
        void shouldThrow_whenTypeUnrecognised() {
            assertThatThrownBy(() -> parser.parse(
                    "{\"annotations\":[{\"type\":\"redact\",\"page\":1,\"x\":0,\"y\":0,\"w\":0.1,\"h\":0.1}]}", PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unrecognised type");
        }

        @Test
        @DisplayName("should throw when the page is outside the document")
        void shouldThrow_whenPageOutsideDocument() {
            assertThatThrownBy(() -> parser.parse(
                    "{\"annotations\":[{\"type\":\"highlight\",\"page\":9,\"x\":0,\"y\":0,\"w\":0.1,\"h\":0.1}]}", PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page 9");
        }

        @Test
        @DisplayName("should throw when a coordinate falls outside the page")
        void shouldThrow_whenCoordinateOutsidePage() {
            assertThatThrownBy(() -> parser.parse(
                    "{\"annotations\":[{\"type\":\"highlight\",\"page\":1,\"x\":1.4,\"y\":0,\"w\":0.1,\"h\":0.1}]}", PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside the page");
        }

        @Test
        @DisplayName("should throw when a mark extends past the page edge")
        void shouldThrow_whenMarkExtendsPastEdge() {
            assertThatThrownBy(() -> parser.parse(
                    "{\"annotations\":[{\"type\":\"highlight\",\"page\":1,\"x\":0.8,\"y\":0,\"w\":0.5,\"h\":0.1}]}", PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("past the edge");
        }

        @Test
        @DisplayName("should throw when the colour is not on the allowlist")
        void shouldThrow_whenColourNotAllowed() {
            assertThatThrownBy(() -> parser.parse(
                    "{\"annotations\":[{\"type\":\"highlight\",\"page\":1,\"x\":0,\"y\":0,\"w\":0.1,\"h\":0.1,"
                            + "\"color\":\"url(javascript:alert(1))\"}]}", PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("colour");
        }

        @Test
        @DisplayName("should throw when the annotation count exceeds the ceiling")
        void shouldThrow_whenTooManyAnnotations() {
            StringBuilder json = new StringBuilder("{\"annotations\":[");
            for (int i = 0; i <= DocumentAnnotationParser.MAX_ANNOTATIONS; i++) {
                json.append(i > 0 ? "," : "")
                        .append("{\"type\":\"highlight\",\"page\":1,\"x\":0,\"y\":0,\"w\":0.1,\"h\":0.1}");
            }
            json.append("]}");

            assertThatThrownBy(() -> parser.parse(json.toString(), PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(DocumentAnnotationParser.MAX_ANNOTATIONS));
        }

        @Test
        @DisplayName("should throw when a stroke carries more points than permitted")
        void shouldThrow_whenStrokeTooDense() {
            StringBuilder points = new StringBuilder();
            for (int i = 0; i <= DocumentAnnotationParser.MAX_POINTS_PER_STROKE; i++) {
                points.append(i > 0 ? "," : "").append("[0.1,0.1]");
            }
            String json = "{\"annotations\":[{\"type\":\"ink\",\"page\":1,\"points\":[" + points + "]}]}";

            assertThatThrownBy(() -> parser.parse(json, PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(DocumentAnnotationParser.MAX_POINTS_PER_STROKE));
        }

        @Test
        @DisplayName("should throw when text exceeds the length limit")
        void shouldThrow_whenTextTooLong() {
            String text = "a".repeat(DocumentAnnotationParser.MAX_TEXT_LENGTH + 1);
            String json = "{\"annotations\":[{\"type\":\"text\",\"page\":1,\"x\":0,\"y\":0,\"w\":0.5,\"h\":0.05,"
                    + "\"text\":\"" + text + "\"}]}";

            assertThatThrownBy(() -> parser.parse(json, PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(DocumentAnnotationParser.MAX_TEXT_LENGTH));
        }

        @Test
        @DisplayName("should throw when the font size is outside the permitted range")
        void shouldThrow_whenFontSizeOutOfRange() {
            String json = "{\"annotations\":[{\"type\":\"text\",\"page\":1,\"x\":0,\"y\":0,\"w\":0.5,\"h\":0.05,"
                    + "\"text\":\"hi\",\"fontSize\":400}]}";

            assertThatThrownBy(() -> parser.parse(json, PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Font size");
        }

        @Test
        @DisplayName("should throw when the payload is not JSON")
        void shouldThrow_whenPayloadNotJson() {
            assertThatThrownBy(() -> parser.parse("not json at all", PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("valid JSON");
        }

        @Test
        @DisplayName("should throw when the body is empty")
        void shouldThrow_whenBodyEmpty() {
            assertThatThrownBy(() -> parser.parse("", PAGES))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * Annotation text is a provider's note about a patient. A rejection message travels
         * back to the browser and into logs, so it must name the rule, never the content.
         */
        @Test
        @DisplayName("should keep annotation content out of the rejection message")
        void shouldOmitContent_whenRejectingText() {
            String phi = "PatientNameCarriedInTheNote";
            String json = "{\"annotations\":[{\"type\":\"text\",\"page\":99,\"x\":0,\"y\":0,\"w\":0.5,\"h\":0.05,"
                    + "\"text\":\"" + phi + "\"}]}";

            assertThatThrownBy(() -> parser.parse(json, PAGES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(phi);
        }
    }
}
