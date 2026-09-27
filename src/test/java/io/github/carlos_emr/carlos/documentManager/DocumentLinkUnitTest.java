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
package io.github.carlos_emr.carlos.documentManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DocumentLink}, the Add Link URL allowlist and stored-HTML builder.
 *
 * <p>Regression coverage for GitHub issue #3949: {@code https://} links were rewritten to
 * {@code http://https://...}, and the raw URL was concatenated into an inline
 * {@code window.location='...'} script with no scheme check or quote escaping.</p>
 *
 * @since 2026-09-26
 */
@DisplayName("DocumentLink")
@Tag("unit")
@Tag("document")
class DocumentLinkUnitTest {

    @Nested
    @DisplayName("normalizeUrl")
    class NormalizeUrl {

        @Test
        void shouldKeepHttpsUrl_withoutPrependingHttp() {
            assertThat(DocumentLink.normalizeUrl("https://example.org/path?q=1#frag"))
                    .hasValue("https://example.org/path?q=1#frag");
        }

        @Test
        void shouldKeepHttpUrl_asEntered() {
            assertThat(DocumentLink.normalizeUrl("http://example.org")).hasValue("http://example.org");
        }

        @Test
        void shouldPrependHttps_forSchemelessHost() {
            assertThat(DocumentLink.normalizeUrl("www.example.org/a")).hasValue("https://www.example.org/a");
        }

        @Test
        void shouldPrependHttps_forSchemelessHostWithPort() {
            assertThat(DocumentLink.normalizeUrl("localhost:8080/carlos"))
                    .hasValue("https://localhost:8080/carlos");
        }

        @Test
        void shouldPrependHttpsScheme_forProtocolRelativeUrl() {
            assertThat(DocumentLink.normalizeUrl("//example.org/x")).hasValue("https://example.org/x");
        }

        @Test
        void shouldTrimWhitespace_aroundUrl() {
            assertThat(DocumentLink.normalizeUrl("  https://example.org  \n")).hasValue("https://example.org");
        }

        @Test
        void shouldLowercaseScheme_whenEnteredInUppercase() {
            assertThat(DocumentLink.normalizeUrl("HTTPS://Example.org/Path")).hasValue("https://Example.org/Path");
        }

        @Test
        void shouldPercentEncodeNonAscii_inPath() {
            assertThat(DocumentLink.normalizeUrl("https://example.org/café"))
                    .hasValue("https://example.org/caf%C3%A9");
        }

        @Test
        void shouldPreserveUnicodeSequence_inDecomposedPathAndQuery() {
            assertThat(DocumentLink.normalizeUrl("https://example.org/cafe\u0301?q=e\u0301"))
                    .hasValue("https://example.org/cafe%CC%81?q=e%CC%81");
        }

        @Test
        void shouldKeepExistingEscapes_withoutDoubleEncoding() {
            assertThat(DocumentLink.normalizeUrl("https://example.org/%2F?q=café#résumé"))
                    .hasValue("https://example.org/%2F?q=caf%C3%A9#r%C3%A9sum%C3%A9");
        }

        @ParameterizedTest
        @ValueSource(strings = {"example.org:8443/path", "localhost:8080/carlos", "127.0.0.1:443/", "[::1]:443/"})
        void shouldAcceptSchemelessPort_forUnambiguousHost(String url) {
            assertThat(DocumentLink.normalizeUrl(url)).hasValue("https://" + url);
        }

        @Test
        void shouldNormalizeWithoutStackOverflow_whenShorthandHasManyHostLabels() {
            String candidate = "a.".repeat(20_000) + "example.org:8443/path?q=1#fragment";
            assertThat(DocumentLink.normalizeUrl(candidate)).hasValue("https://" + candidate);
        }

        @Test
        void shouldRejectWithoutStackOverflow_whenLongShorthandHasInvalidPort() {
            String candidate = "a.".repeat(20_000) + "example.org:8443x/path";
            assertThat(DocumentLink.normalizeUrl(candidate)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"localhost:443@evil.example", "example.org:443@evil.example", "example.org:",
                "example.org:/path", "example.org:443x/path", "example.org:\u0663/path"})
        void shouldRejectShorthand_whenPortIsMissingOrNotAsciiNumeric(String candidate) {
            assertThat(DocumentLink.normalizeUrl(candidate)).isEmpty();
        }

        @Test
        void shouldAcceptSingleQuote_asLegalUriCharacter() {
            // A single quote is legal in a URI; it is kept here and HTML-encoded on output.
            assertThat(DocumentLink.normalizeUrl("https://example.org/it's")).hasValue("https://example.org/it's");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "javascript:alert(1)",
                "JavaScript:alert(document.cookie)",
                " javascript:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "file:///etc/passwd",
                "mailto:someone@example.org",
                "ftp://example.org/file",
                "vbscript:msgbox(1)", "javascript:1", "data:1", "ftp:1", "custom:123", "HTTPS:443"
        })
        void shouldRejectUrl_forNonWebScheme(String url) {
            assertThat(DocumentLink.normalizeUrl(url)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://example.org/\"onmouseover=\"alert(1)",
                "https://example.org/<script>",
                "https://exa mple.org",
                "Enter Link URL",
                "https://",
                "http:example.org",
                "https:///path-only", "https://:443", "https://user@", "https://user@:80/path",
                "https://example.org:bad/path", "https://example.org:65536/path",
                "https://example.org:-1/path", "https://[::1]:99999/", "https://example.org/\uD800"
        })
        void shouldRejectUrl_forMalformedOrUnsafeCharacters(String url) {
            assertThat(DocumentLink.normalizeUrl(url)).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        void shouldRejectUrl_forBlankInput(String url) {
            assertThat(DocumentLink.normalizeUrl(url)).isEmpty();
        }
    }

    @Nested
    @DisplayName("toRedirectHtml")
    class ToRedirectHtml {

        @Test
        void shouldEmitMetaRefreshAndAnchor_withoutInlineScript() {
            String html = DocumentLink.toRedirectHtml("https://example.org/a?b=1&c=2");

            assertThat(html)
                    .contains("<meta http-equiv=\"refresh\" content=\"0; url=https://example.org/a?b=1&amp;c=2\">")
                    .contains("<a href=\"https://example.org/a?b=1&amp;c=2\" rel=\"noopener noreferrer\">")
                    .contains("<meta name=\"referrer\" content=\"no-referrer\">")
                    .doesNotContainIgnoringCase("<script")
                    .doesNotContain("window.location");
        }

        @Test
        void shouldEncodeQuotes_whenUrlContainsSingleQuote() {
            String html = DocumentLink.toRedirectHtml(DocumentLink.normalizeUrl("https://example.org/it's").orElseThrow());

            // Both attribute contexts are encoded; a quote in the anchor's text content is inert.
            assertThat(html)
                    .contains("url=https://example.org/it&#39;s\"")
                    .contains("href=\"https://example.org/it&#39;s\"")
                    .doesNotContain("url=https://example.org/it's")
                    .doesNotContain("href=\"https://example.org/it's");
        }
    }
}
