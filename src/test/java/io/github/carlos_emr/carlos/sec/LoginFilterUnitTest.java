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
}
