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
package io.github.carlos_emr.carlos.sec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LoginFilter#inListOfExemptions(String, String, String[])}.
 *
 * <p>Validates that URL exemption matching enforces proper path boundaries
 * to prevent authentication bypass via crafted URLs (CWE-287).</p>
 *
 * @since 2026-04-08
 */
@Tag("unit")
@Tag("security")
@DisplayName("LoginFilter URL exemption matching")
class LoginFilterUnitTest {

    private static final String CONTEXT_PATH = "/carlos";
    private LoginFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoginFilter();
    }

    @Nested
    @DisplayName("Exact path matching")
    class ExactPathMatching {

        @Test
        @DisplayName("should match exact exempt URL")
        void shouldMatchExactExemptUrl() {
            String[] exemptUrls = {"/login.do"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/login.do", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match URL with extra characters appended")
        void shouldNotMatchUrl_withExtraCharactersAppended() {
            String[] exemptUrls = {"/login.do"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/login.doEvil", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should not match unrelated URL")
        void shouldNotMatchUnrelatedUrl() {
            String[] exemptUrls = {"/login.do"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/admin/settings.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("Directory prefix matching (trailing slash)")
    class DirectoryPrefixMatching {

        @Test
        @DisplayName("should match subpath under directory exempt URL")
        void shouldMatchSubpath_underDirectoryExemptUrl() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws/someService", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should match exact directory URL")
        void shouldMatchExactDirectoryUrl() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws/", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should match nested subpath under directory exempt URL")
        void shouldMatchNestedSubpath_underDirectoryExemptUrl() {
            String[] exemptUrls = {"/mfa/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/mfa/verify/code", CONTEXT_PATH, exemptUrls)).isTrue();
        }
    }

    @Nested
    @DisplayName("Path boundary enforcement — prevents authentication bypass")
    class PathBoundaryEnforcement {

        @Test
        @DisplayName("should not match crafted URL appending to css/bootstrap prefix")
        void shouldNotMatchCraftedUrl_appendingToCssBootstrapPrefix() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/css/bootstrapEvil.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should not match crafted URL appending .jsp to exempt path")
        void shouldNotMatchCraftedUrl_appendingJspToExemptPath() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/css/bootstrap.jsp", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should match css/bootstrap subpath with path separator")
        void shouldMatchCssBootstrapSubpath_withPathSeparator() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/css/bootstrap/file.css", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match crafted URL appending to images/Oscar.ico")
        void shouldNotMatchCraftedUrl_appendingToImagePath() {
            String[] exemptUrls = {"/images/Oscar.ico"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/images/Oscar.ico.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should not match crafted URL appending to js/bootstrap prefix")
        void shouldNotMatchCraftedUrl_appendingToJsBootstrapPrefix() {
            String[] exemptUrls = {"/js/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/js/bootstrapAdmin.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should match js/bootstrap exactly")
        void shouldMatchJsBootstrapExactly() {
            String[] exemptUrls = {"/js/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/js/bootstrap", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match crafted URL appending to csrfguard")
        void shouldNotMatchCraftedUrl_appendingToCsrfguard() {
            String[] exemptUrls = {"/csrfguard"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/csrfguardEvil.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("Context root handling")
    class ContextRootHandling {

        @Test
        @DisplayName("should treat context root as index.jsp")
        void shouldTreatContextRoot_asIndexJsp() {
            String[] exemptUrls = {"/index.jsp"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH, CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should treat context root with trailing slash as index.jsp")
        void shouldTreatContextRootWithTrailingSlash_asIndexJsp() {
            String[] exemptUrls = {"/index.jsp"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/", CONTEXT_PATH, exemptUrls)).isTrue();
        }
    }

    @Nested
    @DisplayName("Empty context path")
    class EmptyContextPath {

        @Test
        @DisplayName("should match with empty context path")
        void shouldMatch_withEmptyContextPath() {
            String[] exemptUrls = {"/login.do"};
            assertThat(filter.inListOfExemptions(
                    "/login.do", "", exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match crafted URL with empty context path")
        void shouldNotMatchCraftedUrl_withEmptyContextPath() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    "/css/bootstrapEvil.do", "", exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("No match scenarios")
    class NoMatchScenarios {

        @Test
        @DisplayName("should return false for empty exemption list")
        void shouldReturnFalse_forEmptyExemptionList() {
            String[] exemptUrls = {};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/login.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should return false for completely unrelated URL")
        void shouldReturnFalse_forCompletelyUnrelatedUrl() {
            String[] exemptUrls = {"/login.do", "/logout.jsp"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/provider/dashboard.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("URI normalization — path parameters")
    class PathParameterNormalization {

        @Test
        @DisplayName("should match exempt URL when path parameter is appended")
        void shouldMatchExemptUrl_whenPathParameterAppended() {
            String[] exemptUrls = {"/login.do"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/login.do;jsessionid=abc123", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match non-exempt URL when path parameter is stripped")
        void shouldNotMatchNonExemptUrl_whenPathParameterStripped() {
            String[] exemptUrls = {"/login.do"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/admin/settings.do;jsessionid=abc123", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should match directory exempt URL with path parameter")
        void shouldMatchDirectoryExemptUrl_withPathParameter() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws/service;v=1.0", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should match exempt URL with path parameters in multiple segments")
        void shouldMatchExemptUrl_withPathParametersInMultipleSegments() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws;param1=a/service;param2=b", CONTEXT_PATH, exemptUrls)).isTrue();
        }
    }

    @Nested
    @DisplayName("URI normalization — repeated slashes")
    class RepeatedSlashNormalization {

        @Test
        @DisplayName("should match exempt URL with double slashes collapsed")
        void shouldMatchExemptUrl_withDoubleSlashesCollapsed() {
            String[] exemptUrls = {"/login.do"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "//login.do", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match non-exempt URL after slash collapsing")
        void shouldNotMatchNonExemptUrl_afterSlashCollapsing() {
            String[] exemptUrls = {"/login.do"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "//admin//settings.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("URI normalization — dot segment resolution")
    class DotSegmentNormalization {

        @Test
        @DisplayName("should not match non-exempt URL disguised with dot-dot traversal")
        void shouldNotMatchNonExemptUrl_disguisedWithDotDotTraversal() {
            String[] exemptUrls = {"/ws/"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/ws/../admin/settings.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }

        @Test
        @DisplayName("should match exempt URL with redundant dot segment")
        void shouldMatchExemptUrl_withRedundantDotSegment() {
            String[] exemptUrls = {"/login.do"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/./login.do", CONTEXT_PATH, exemptUrls)).isTrue();
        }

        @Test
        @DisplayName("should not match non-exempt URL via dot-dot into exempt directory")
        void shouldNotMatchNonExemptUrl_viaDotDotIntoExemptDirectory() {
            String[] exemptUrls = {"/css/bootstrap"};
            assertThat(filter.inListOfExemptions(
                    CONTEXT_PATH + "/css/bootstrap/../../admin/secret.do", CONTEXT_PATH, exemptUrls)).isFalse();
        }
    }

    @Nested
    @DisplayName("normalizeUri static method")
    class NormalizeUri {

        @Test
        @DisplayName("should strip path parameters")
        void shouldStripPathParameters() {
            assertThat(LoginFilter.normalizeUri("/carlos/login.do;jsessionid=abc"))
                    .isEqualTo("/carlos/login.do");
        }

        @Test
        @DisplayName("should strip path parameters from multiple segments")
        void shouldStripPathParameters_fromMultipleSegments() {
            assertThat(LoginFilter.normalizeUri("/carlos/ws;param1=a/service;param2=b"))
                    .isEqualTo("/carlos/ws/service");
        }

        @Test
        @DisplayName("should collapse repeated slashes")
        void shouldCollapseRepeatedSlashes() {
            assertThat(LoginFilter.normalizeUri("//carlos///login.do"))
                    .isEqualTo("/carlos/login.do");
        }

        @Test
        @DisplayName("should resolve dot-dot segments")
        void shouldResolveDotDotSegments() {
            assertThat(LoginFilter.normalizeUri("/carlos/ws/../admin/secret.do"))
                    .isEqualTo("/carlos/admin/secret.do");
        }

        @Test
        @DisplayName("should resolve single dot segments")
        void shouldResolveSingleDotSegments() {
            assertThat(LoginFilter.normalizeUri("/carlos/./login.do"))
                    .isEqualTo("/carlos/login.do");
        }

        @Test
        @DisplayName("should preserve trailing slash")
        void shouldPreserveTrailingSlash() {
            assertThat(LoginFilter.normalizeUri("/carlos/ws/"))
                    .isEqualTo("/carlos/ws/");
        }

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNullInput() {
            assertThat(LoginFilter.normalizeUri(null)).isNull();
        }

        @Test
        @DisplayName("should handle empty input")
        void shouldHandleEmptyInput() {
            assertThat(LoginFilter.normalizeUri("")).isEmpty();
        }

        @Test
        @DisplayName("should handle combined normalization")
        void shouldHandleCombinedNormalization() {
            assertThat(LoginFilter.normalizeUri("//carlos/./ws/../admin///secret.do;jsessionid=x"))
                    .isEqualTo("/carlos/admin/secret.do");
        }
    }
}
