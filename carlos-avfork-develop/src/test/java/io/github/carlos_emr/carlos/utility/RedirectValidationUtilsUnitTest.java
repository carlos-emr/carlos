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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RedirectValidationUtils}.
 *
 * <p>Validates that the open-redirect guard correctly blocks all known bypass
 * vectors while accepting valid same-origin relative paths.  Covers every
 * attack shape listed in the CodeQL #5909 verification checklist.</p>
 *
 * @since 2026-04-02
 */
@Tag("unit")
@Tag("security")
@DisplayName("RedirectValidationUtils")
class RedirectValidationUtilsUnitTest {

    // -----------------------------------------------------------------------
    // Rejected URLs
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("should reject")
    class Rejected {

        @ParameterizedTest(name = "null / empty: [{0}]")
        @NullAndEmptySource
        @DisplayName("null and empty inputs")
        void shouldRejectRedirect_whenNullOrEmpty(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
        }

        @ParameterizedTest(name = "protocol-relative: {0}")
        @ValueSource(strings = {
            "//evil.com",
            "//evil.com/path",
            "///evil.com"
        })
        @DisplayName("protocol-relative URLs (double and triple slash)")
        void shouldRejectRedirect_whenProtocolRelativeUrl(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
        }

        @ParameterizedTest(name = "absolute URL: {0}")
        @ValueSource(strings = {
            "https://evil.com",
            "http://evil.com",
            "https://evil.com/steal?token=abc",
            "ftp://evil.com"
        })
        @DisplayName("absolute HTTP/S and other scheme URLs")
        void shouldRejectRedirect_whenAbsoluteHttpUrl(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
        }

        @ParameterizedTest(name = "javascript scheme: {0}")
        @ValueSource(strings = {
            "javascript:alert(1)",
            "javascript:void(0)",
            "javascript://comment%0aalert(1)"
        })
        @DisplayName("javascript: scheme")
        void shouldRejectRedirect_whenJavascriptScheme(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
        }

        @ParameterizedTest(name = "data scheme: {0}")
        @ValueSource(strings = {
            "data:text/html,<script>alert(1)</script>",
            "data:text/html;base64,PHNjcmlwdD4="
        })
        @DisplayName("data: scheme")
        void shouldRejectRedirect_whenDataScheme(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
        }

        @Test
        @DisplayName("vbscript: scheme")
        void shouldRejectRedirect_whenVbscriptScheme() {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("vbscript:MsgBox")).isFalse();
        }

        @ParameterizedTest(name = "backslash bypass: {0}")
        @ValueSource(strings = {
            "/\\evil.com",
            "\\\\evil.com\\path",
            "\\evil.com",
            "/path\\..\\evil.com",
            "/%5cevil.com",
            "/%5Cevil.com",
            "/path%5c..%5cevil.com",
            "%5c%5cevil.com"
        })
        @DisplayName("backslash-based bypass vectors (literal and percent-encoded)")
        void shouldRejectRedirect_whenBackslashBypass(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
        }

        @ParameterizedTest(name = "path traversal: {0}")
        @ValueSource(strings = {
            // Slash-delimited forms
            "/../evil",
            "/foo/../../../evil",
            "/carlos/module/../../etc/passwd",
            // Relative dot-segment forms
            "../evil",
            "provider/..",
            "/..",
            "..",
            // Percent-encoded dot-segment (decoded by URI.getPath() before check)
            "/%2e%2e/evil"
        })
        @DisplayName("path-traversal sequences")
        void shouldRejectRedirect_whenPathTraversal(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
        }

        @ParameterizedTest(name = "whitespace / control char prefix: [{0}]")
        @ValueSource(strings = {
            "\t//evil.com",
            "\n//evil.com",
            " //evil.com"
        })
        @DisplayName("leading whitespace / control characters (URISyntaxException)")
        void shouldRejectRedirect_whenLeadingWhitespaceOrControl(String url) {
            // Java URI rejects raw whitespace/control chars → URISyntaxException → false
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
        }

        @Test
        @DisplayName("null-byte injection (/path%00javascript:...)")
        void shouldRejectRedirect_whenNullByteInjection() {
            // %00 in path is syntactically valid but browsers do not split on it;
            // the result is a plain relative path, not a scheme change.
            // Java URI alone would treat this as a path — no scheme, no authority.
            // The validator now rejects it as defense-in-depth because encoded
            // control characters (%00-%1F) have no legitimate use in redirect paths.
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("/path%00javascript:alert(1)")).isFalse();
        }

        @Test
        @DisplayName("syntactically invalid URI")
        void shouldRejectRedirect_whenInvalidUri() {
            // Unbalanced bracket is invalid per RFC 3986
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("/[invalid")).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Accepted URLs
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("should accept")
    class Accepted {

        @ParameterizedTest(name = "valid relative path: {0}")
        @ValueSource(strings = {
            "/provider/providercontrol.jsp",
            "/carlos/provider/providercontrol.jsp",
            "/billing/CA/ON/billingON.jsp",
            "/demographic/DemographicSearch",
            "/index",
            "/provider/providercontrol.jsp?status=active",
            "/provider/providercontrol.jsp?a=1&b=2"
        })
        @DisplayName("relative paths")
        void shouldAcceptRedirect_whenRelativePath(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isTrue();
        }

        @Test
        @DisplayName("path with safe dot-segment (single dot)")
        void shouldAcceptRedirect_whenSingleDotSegment() {
            // A single-dot segment (/./foo) is not a traversal and is safe
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("/./provider/providercontrol.jsp")).isTrue();
        }

        @Test
        @DisplayName("path with .. in filename (not a traversal sequence)")
        void shouldAcceptRedirect_whenDoubleDotInFilenameOnly() {
            // /foo..bar is NOT a traversal — the regex only matches .. as a complete path segment
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("/provider/report..jsp")).isTrue();
        }

        @Test
        @DisplayName("path containing @ (userinfo-trick is not an attack for relative URIs)")
        void shouldAcceptRedirect_whenPathContainsAtSign() {
            // /path@evil.com has no authority component — the @ is just a literal
            // character in the path.  Java URI parser and browsers both treat it
            // as a plain path, so it resolves on the same origin.
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("/path@evil.com/foo")).isTrue();
        }

        @Test
        @DisplayName("percent-encoded forward slashes (%2f%2f) — safe relative path")
        void shouldAcceptRedirect_whenEncodedForwardSlashes() {
            // %2f is the percent-encoding of '/'.  RFC 3986 §2.2 says reserved
            // characters keep their special meaning ONLY in unencoded form, so
            // browsers treat %2f%2fevil.com as a single path segment, NOT as a
            // protocol-relative //evil.com reference.  Accepting it is correct.
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("%2f%2fevil.com")).isTrue();
        }
    }
}
