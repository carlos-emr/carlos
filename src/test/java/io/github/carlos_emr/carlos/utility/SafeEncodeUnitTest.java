/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.owasp.encoder.Encode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SafeEncode}.
 *
 * <p>Verifies two invariants for every delegated method:
 * <ol>
 *   <li>{@code null} input → empty string (scalar) or nothing written (writer).</li>
 *   <li>Non-null input produces output bit-identical to the corresponding
 *       {@link Encode} method.</li>
 * </ol>
 *
 * <p>These tests exist because {@code Encode.forXxx(null)} returns the literal
 * string {@code "null"} — a silent regression source after the mass migration
 * from {@code <c:out>} (which renders null as empty).
 *
 * @since 2026-04-18
 */
@Tag("unit")
@Tag("encoding")
@DisplayName("SafeEncode null-safe wrapper")
class SafeEncodeUnitTest {

    private static final String[] NON_NULL_INPUTS = {
            "",
            "plain text",
            "<script>alert('xss')</script>",
            "O'Brien & Smith",
            "A \"quoted\" value",
            "line1\nline2\r\ntab\tend",
            "japanese 日本語 chars",
            "path/to/file?id=123&flag=true"
    };

    /** Prove the OWASP baseline: Encode.* returns the literal String "null" for null input. */
    @Nested
    @DisplayName("Baseline — OWASP Encode returns literal 'null' for null input")
    class Baseline {

        @Test
        @DisplayName("Encode.forHtmlContent(null) returns literal \"null\"")
        void owasp_forHtmlContent_null_returnsLiteralNull() {
            assertThat(Encode.forHtmlContent(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("Encode.forHtmlAttribute(null) returns literal \"null\"")
        void owasp_forHtmlAttribute_null_returnsLiteralNull() {
            assertThat(Encode.forHtmlAttribute(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("Encode.forUriComponent(null) returns literal \"null\"")
        void owasp_forUriComponent_null_returnsLiteralNull() {
            assertThat(Encode.forUriComponent(null)).isEqualTo("null");
        }
    }

    /** SafeEncode scalar methods: null → "". */
    @Nested
    @DisplayName("Null input renders as empty string (scalar overloads)")
    class NullScalar {

        @Test
        void shouldReturnEmpty_forHtml_whenValueIsNull() {
            assertThat(SafeEncode.forHtml(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forHtmlContent_whenValueIsNull() {
            assertThat(SafeEncode.forHtmlContent(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forHtmlContentWithBreaks_whenValueIsNull() {
            assertThat(SafeEncode.forHtmlContentWithBreaks(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forHtmlAttribute_whenValueIsNull() {
            assertThat(SafeEncode.forHtmlAttribute(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forHtmlUnquotedAttribute_whenValueIsNull() {
            assertThat(SafeEncode.forHtmlUnquotedAttribute(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forCssString_whenValueIsNull() {
            assertThat(SafeEncode.forCssString(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forCssUrl_whenValueIsNull() {
            assertThat(SafeEncode.forCssUrl(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forUri_whenValueIsNull() {
            assertThat(SafeEncode.forUri(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forUriComponent_whenValueIsNull() {
            assertThat(SafeEncode.forUriComponent(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forXml_whenValueIsNull() {
            assertThat(SafeEncode.forXml(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forXmlContent_whenValueIsNull() {
            assertThat(SafeEncode.forXmlContent(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forXmlAttribute_whenValueIsNull() {
            assertThat(SafeEncode.forXmlAttribute(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forXmlComment_whenValueIsNull() {
            assertThat(SafeEncode.forXmlComment(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forXml11_whenValueIsNull() {
            assertThat(SafeEncode.forXml11(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forXml11Content_whenValueIsNull() {
            assertThat(SafeEncode.forXml11Content(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forXml11Attribute_whenValueIsNull() {
            assertThat(SafeEncode.forXml11Attribute(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forCDATA_whenValueIsNull() {
            assertThat(SafeEncode.forCDATA(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forJava_whenValueIsNull() {
            assertThat(SafeEncode.forJava(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forJavaScript_whenValueIsNull() {
            assertThat(SafeEncode.forJavaScript(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forJavaScriptAttribute_whenValueIsNull() {
            assertThat(SafeEncode.forJavaScriptAttribute(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forJavaScriptBlock_whenValueIsNull() {
            assertThat(SafeEncode.forJavaScriptBlock(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_forJavaScriptSource_whenValueIsNull() {
            assertThat(SafeEncode.forJavaScriptSource(null)).isEmpty();
        }
    }

    /** SafeEncode writer methods: null → nothing written. */
    @Nested
    @DisplayName("Null input writes nothing (writer overloads)")
    class NullWriter {

        @Test
        void shouldWriteEmpty_forHtmlContent_whenValueIsNull() throws IOException {
            StringWriter sw = new StringWriter();
            SafeEncode.forHtmlContent(sw, null);
            assertThat(sw.toString()).isEmpty();
        }

        @Test
        void shouldWriteEmpty_forHtmlAttribute_whenValueIsNull() throws IOException {
            StringWriter sw = new StringWriter();
            SafeEncode.forHtmlAttribute(sw, null);
            assertThat(sw.toString()).isEmpty();
        }

        @Test
        void shouldWriteEmpty_forJavaScript_whenValueIsNull() throws IOException {
            StringWriter sw = new StringWriter();
            SafeEncode.forJavaScript(sw, null);
            assertThat(sw.toString()).isEmpty();
        }

        @Test
        void shouldWriteEmpty_forUriComponent_whenValueIsNull() throws IOException {
            StringWriter sw = new StringWriter();
            SafeEncode.forUriComponent(sw, null);
            assertThat(sw.toString()).isEmpty();
        }
    }

    /** Non-null inputs must match Encode.* output exactly. */
    @Nested
    @DisplayName("Non-null input matches OWASP Encode output character-for-character")
    class NonNullParity {

        @Test
        void shouldMatchEncode_forHtmlContent_forAllInputs() {
            for (String input : NON_NULL_INPUTS) {
                assertThat(SafeEncode.forHtmlContent(input))
                        .as("forHtmlContent(%s)", input)
                        .isEqualTo(Encode.forHtmlContent(input));
            }
        }

        @Test
        void shouldEncodeHtmlAndPreserveLineBreaks_forHtmlContentWithBreaks() {
            assertThat(SafeEncode.forHtmlContentWithBreaks("line1\r\n<script>alert('xss')</script>\rline3\n"))
                    .isEqualTo("line1<br/>&lt;script&gt;alert('xss')&lt;/script&gt;<br/>line3<br/>");
        }

        @Test
        void shouldMatchEncode_forHtmlAttribute_forAllInputs() {
            for (String input : NON_NULL_INPUTS) {
                assertThat(SafeEncode.forHtmlAttribute(input))
                        .as("forHtmlAttribute(%s)", input)
                        .isEqualTo(Encode.forHtmlAttribute(input));
            }
        }

        @Test
        void shouldMatchEncode_forJavaScript_forAllInputs() {
            for (String input : NON_NULL_INPUTS) {
                assertThat(SafeEncode.forJavaScript(input))
                        .as("forJavaScript(%s)", input)
                        .isEqualTo(Encode.forJavaScript(input));
            }
        }

        @Test
        void shouldMatchEncode_forUriComponent_forAllInputs() {
            for (String input : NON_NULL_INPUTS) {
                assertThat(SafeEncode.forUriComponent(input))
                        .as("forUriComponent(%s)", input)
                        .isEqualTo(Encode.forUriComponent(input));
            }
        }

        @Test
        void shouldMatchEncode_forCssString_forAllInputs() {
            for (String input : NON_NULL_INPUTS) {
                assertThat(SafeEncode.forCssString(input))
                        .as("forCssString(%s)", input)
                        .isEqualTo(Encode.forCssString(input));
            }
        }

        @Test
        void shouldMatchEncode_forXml_forAllInputs() {
            for (String input : NON_NULL_INPUTS) {
                assertThat(SafeEncode.forXml(input))
                        .as("forXml(%s)", input)
                        .isEqualTo(Encode.forXml(input));
            }
        }

        @Test
        void shouldMatchEncode_forJava_forAllInputs() {
            for (String input : NON_NULL_INPUTS) {
                assertThat(SafeEncode.forJava(input))
                        .as("forJava(%s)", input)
                        .isEqualTo(Encode.forJava(input));
            }
        }

        @Test
        void shouldEncodeScriptPayload_forHtmlContent_producingSafeOutput() {
            // Spot-check that XSS vectors are still neutralized.
            String payload = "<script>alert('xss')</script>";
            String encoded = SafeEncode.forHtmlContent(payload);
            assertThat(encoded).doesNotContain("<script>");
            assertThat(encoded).contains("&lt;").contains("&gt;");
        }
    }

    /** Writer-variant matches scalar-variant output for non-null inputs. */
    @Nested
    @DisplayName("Writer overloads match scalar overloads for non-null input")
    class WriterParity {

        @Test
        void shouldWriteSameAsScalar_forHtmlContent_forAllInputs() throws IOException {
            for (String input : NON_NULL_INPUTS) {
                StringWriter sw = new StringWriter();
                SafeEncode.forHtmlContent(sw, input);
                assertThat(sw.toString())
                        .as("writer forHtmlContent(%s)", input)
                        .isEqualTo(SafeEncode.forHtmlContent(input));
            }
        }

        @Test
        void shouldWriteSameAsScalar_forJavaScript_forAllInputs() throws IOException {
            for (String input : NON_NULL_INPUTS) {
                StringWriter sw = new StringWriter();
                SafeEncode.forJavaScript(sw, input);
                assertThat(sw.toString())
                        .as("writer forJavaScript(%s)", input)
                        .isEqualTo(SafeEncode.forJavaScript(input));
            }
        }
    }
}
