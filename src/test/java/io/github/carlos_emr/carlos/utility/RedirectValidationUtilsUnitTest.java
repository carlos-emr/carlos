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
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RedirectValidationUtils}.
 *
 * <p>Validates that the open-redirect guard correctly blocks all known bypass
 * vectors while accepting valid same-origin relative paths.</p>
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

        @Test
        @DisplayName("null")
        void shouldRejectRedirect_whenNull() {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(null)).isFalse();
        }

        @Test
        @DisplayName("protocol-relative URL (//evil.com)")
        void shouldRejectRedirect_whenProtocolRelativeUrl() {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("//evil.com")).isFalse();
        }

        @Test
        @DisplayName("protocol-relative URL with path (//evil.com/path)")
        void shouldRejectRedirect_whenProtocolRelativeUrlWithPath() {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("//evil.com/path")).isFalse();
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

        @ParameterizedTest(name = "backslash bypass: {0}")
        @ValueSource(strings = {
            "/\\evil.com",
            "\\evil.com",
            "/path\\..\\evil.com"
        })
        @DisplayName("backslash-based bypass vectors")
        void shouldRejectRedirect_whenBackslashBypass(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
        }

        @ParameterizedTest(name = "path traversal: {0}")
        @ValueSource(strings = {
            "/../evil",
            "/foo/../../../evil",
            "/carlos/module/../../etc/passwd"
        })
        @DisplayName("path-traversal sequences")
        void shouldRejectRedirect_whenPathTraversal(String url) {
            assertThat(RedirectValidationUtils.isValidRelativeRedirect(url)).isFalse();
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
            "/demographic/demographiccontrol.jsp",
            "/index.jsp",
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
            // /foo..bar is NOT a traversal; only /../ sequences are blocked
            assertThat(RedirectValidationUtils.isValidRelativeRedirect("/provider/report..jsp")).isTrue();
        }
    }
}
