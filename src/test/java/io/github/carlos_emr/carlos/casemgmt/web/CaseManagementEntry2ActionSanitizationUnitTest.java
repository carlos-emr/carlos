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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.commn.model.Admission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the package-private sanitization helpers and reporter team fallback helper on
 * {@link CaseManagementEntry2Action}.
 *
 * <p>These helpers are package-private static methods and do not require a Spring context.
 *
 * @since 2026-04-06
 */
@Tag("unit")
@Tag("security")
@DisplayName("CaseManagementEntry2Action sanitization and reporter team helpers")
class CaseManagementEntry2ActionSanitizationUnitTest {

    // ------------------------------------------------------------------
    // sanitizeFromParam
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("sanitizeFromParam")
    class SanitizeFromParam {

        @Test
        @DisplayName("should return casemgmt when value is casemgmt")
        void shouldReturnCasemgmt_whenValueIsCasemgmt() {
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("casemgmt")).isEqualTo("casemgmt");
        }

        @Test
        @DisplayName("should return null when value is not in whitelist")
        void shouldReturnNull_whenValueNotInWhitelist() {
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("provider")).isNull();
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("billing")).isNull();
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("../admin")).isNull();
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("<script>")).isNull();
        }

        @Test
        @DisplayName("should return null when value is null")
        void shouldReturnNull_whenValueIsNull() {
            assertThat(CaseManagementEntry2Action.sanitizeFromParam(null)).isNull();
        }

        @Test
        @DisplayName("should return null when value is empty string")
        void shouldReturnNull_whenValueIsEmpty() {
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("")).isNull();
        }

        @Test
        @DisplayName("should return null when value differs only in case")
        void shouldReturnNull_whenDifferentCase() {
            // whitelist is case-sensitive to avoid ambiguity; "CASEMGMT" is not accepted
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("CASEMGMT")).isNull();
        }
    }

    // ------------------------------------------------------------------
    // sanitizeNoteSortParam
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("sanitizeNoteSortParam")
    class SanitizeNoteSortParam {

        @Test
        @DisplayName("should accept all whitelisted sort values")
        void shouldAccept_whitelistedSortValues() {
            for (String allowed : new String[]{
                    "observation_date_asc", "observation_date_desc",
                    "providerName", "programName", "roleName", "update_date"}) {
                assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam(allowed))
                        .as("Expected allowed value '%s' to pass", allowed)
                        .isEqualTo(allowed);
            }
        }

        @Test
        @DisplayName("should return null for unknown sort column")
        void shouldReturnNull_forUnknownSortColumn() {
            assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam("evil_column")).isNull();
            assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam("1 OR 1=1")).isNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNullInput() {
            assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty string")
        void shouldReturnNull_forEmptyString() {
            assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam("")).isNull();
        }
    }

    // ------------------------------------------------------------------
    // sanitizeIdFilterArray
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("sanitizeIdFilterArray")
    class SanitizeIdFilterArray {

        @Test
        @DisplayName("should return null when input is null")
        void shouldReturnNull_whenInputIsNull() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(null)).isNull();
        }

        @Test
        @DisplayName("should return empty array when input is empty array")
        void shouldReturnEmpty_whenInputIsEmpty() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[0])).isEmpty();
        }

        @Test
        @DisplayName("should accept the all sentinel a")
        void shouldAccept_allSentinel() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"a"}))
                    .containsExactly("a");
        }

        @Test
        @DisplayName("should accept the none sentinel n")
        void shouldAccept_noneSentinel() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"n"}))
                    .containsExactly("n");
        }

        @Test
        @DisplayName("should accept digit-only numeric IDs")
        void shouldAccept_numericIds() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"1", "42", "1000"}))
                    .containsExactly("1", "42", "1000");
        }

        @Test
        @DisplayName("should drop non-numeric non-sentinel values")
        void shouldDrop_nonNumericValues() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(
                    new String[]{"1", "doctor", "'; DROP TABLE--", "42"}))
                    .containsExactly("1", "42");
        }

        @Test
        @DisplayName("should return empty array when all values are invalid")
        void shouldReturnEmpty_whenAllValuesAreInvalid() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(
                    new String[]{"invalid", "<xss>", "1 OR 1=1"}))
                    .isEmpty();
        }

        @Test
        @DisplayName("should drop null elements within array")
        void shouldDrop_nullElements() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{null, "5", null}))
                    .containsExactly("5");
        }

        @Test
        @DisplayName("should drop negative numbers")
        void shouldDrop_negativeNumbers() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"-1", "5"}))
                    .containsExactly("5");
        }

        @Test
        @DisplayName("should drop alphanumeric mixed values")
        void shouldDrop_alphanumericMixed() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"1abc", "abc1"}))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("isValidInternalRedirect")
    class IsValidInternalRedirect {

        @ParameterizedTest(name = "null or empty redirect: {0}")
        @NullAndEmptySource
        @DisplayName("should reject null and empty URLs")
        void shouldReject_whenUrlNullOrEmpty(String url) {
            assertThat(CaseManagementEntry2Action.isValidInternalRedirect(url)).isFalse();
        }

        @ParameterizedTest(name = "valid relative redirect: {0}")
        @ValueSource(strings = {
                "/provider/providercontrol.jsp",
                "/carlos/provider/providercontrol.jsp",
                "/billing?billRegion=ON&demographic_no=42"
        })
        @DisplayName("should accept slash-prefixed relative URLs")
        void shouldAccept_whenSlashPrefixedRelativeUrls(String url) {
            assertThat(CaseManagementEntry2Action.isValidInternalRedirect(url)).isTrue();
        }

        @ParameterizedTest(name = "absolute redirect: {0}")
        @ValueSource(strings = {
                "https://carlos.example/provider/providercontrol.jsp",
                "http://carlos.example:8080/carlos/provider/providercontrol.jsp",
                "https://carlos.example/carlos/provider/providercontrol.jsp"
        })
        @DisplayName("should reject absolute URLs even when they use the application host")
        void shouldReject_whenAbsoluteUrlsUseApplicationHost(String url) {
            assertThat(CaseManagementEntry2Action.isValidInternalRedirect(url)).isFalse();
        }

        @ParameterizedTest(name = "invalid redirect: {0}")
        @ValueSource(strings = {
                "provider/providercontrol.jsp",
                "//evil.example/path",
                "/\\evil.example",
                "/../admin"
        })
        @DisplayName("should reject non-slash-prefixed and unsafe relative URLs")
        void shouldReject_whenRelativeUrlsUnsafe(String url) {
            assertThat(CaseManagementEntry2Action.isValidInternalRedirect(url)).isFalse();
        }
    }

    @Nested
    @DisplayName("sanitizeInternalRedirect")
    class SanitizeInternalRedirect {

        @Test
        @DisplayName("should return trimmed safe redirect target")
        void shouldReturnTrimmedUrl_whenUrlHasOuterWhitespace() {
            assertThat(CaseManagementEntry2Action.sanitizeInternalRedirect(" \t/provider/providercontrol.jsp \n"))
                    .isEqualTo("/provider/providercontrol.jsp");
        }

        @ParameterizedTest(name = "blank redirect: [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("should return null for blank URLs")
        void shouldReturnNull_whenUrlBlank(String url) {
            assertThat(CaseManagementEntry2Action.sanitizeInternalRedirect(url)).isNull();
        }

        @ParameterizedTest(name = "unsafe redirect: {0}")
        @ValueSource(strings = {
                "https://carlos.example/provider/providercontrol.jsp",
                "//evil.example/path",
                "/\\evil.example",
                "/../admin"
        })
        @DisplayName("should return null for unsafe URLs")
        void shouldReturnNull_whenUrlUnsafe(String url) {
            assertThat(CaseManagementEntry2Action.sanitizeInternalRedirect(url)).isNull();
        }
    }

    @Nested
    @DisplayName("sanitizeChainResultName")
    class SanitizeChainResultName {

        @ParameterizedTest(name = "safe chain result: {0}")
        @ValueSource(strings = {
                "list",
                "view",
                "issueList_ajax"
        })
        @DisplayName("should return whitelisted chain result names")
        void shouldReturn_whenResultNameWhitelisted(String chain) {
            assertThat(CaseManagementEntry2Action.sanitizeChainResultName(chain)).isEqualTo(chain);
        }

        @Test
        @DisplayName("should return trimmed whitelisted chain result name")
        void shouldReturn_whenResultNameHasOuterWhitespace() {
            assertThat(CaseManagementEntry2Action.sanitizeChainResultName(" \tlist \n")).isEqualTo("list");
        }

        @ParameterizedTest(name = "blank chain result: [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("should return null for blank chain result names")
        void shouldReturnNull_whenResultNameBlank(String chain) {
            assertThat(CaseManagementEntry2Action.sanitizeChainResultName(chain)).isNull();
        }

        @ParameterizedTest(name = "unsafe chain result: {0}")
        @ValueSource(strings = {
                "listCPPNotes",
                "windowClose",
                "https://evil.example",
                "/provider/providercontrol.jsp",
                "../admin",
                "list;listCPPNotes"
        })
        @DisplayName("should return null for untrusted chain result names")
        void shouldReturnNull_whenResultNameUntrusted(String chain) {
            assertThat(CaseManagementEntry2Action.sanitizeChainResultName(chain)).isNull();
        }
    }

    @Nested
    @DisplayName("resolveReporterProgramTeamId")
    class ResolveReporterProgramTeamId {

        @Test
        @DisplayName("should return team ID when admission exists")
        void shouldReturnTeamId_whenAdmissionExists() {
            AdmissionManager admissionManager = mock(AdmissionManager.class);
            Admission admission = mock(Admission.class);
            when(admissionManager.getAdmission("7", 42)).thenReturn(admission);
            when(admission.getTeamId()).thenReturn(15);

            assertThat(CaseManagementEntry2Action.resolveReporterProgramTeamId(admissionManager, "7", "42"))
                    .isEqualTo("15");
        }

        @Test
        @DisplayName("should return zero when admission is missing")
        void shouldReturnZero_whenAdmissionIsMissing() {
            AdmissionManager admissionManager = mock(AdmissionManager.class);
            when(admissionManager.getAdmission("7", 42)).thenReturn(null);

            assertThat(CaseManagementEntry2Action.resolveReporterProgramTeamId(admissionManager, "7", "42"))
                    .isEqualTo("0");
        }

        @Test
        @DisplayName("should return zero when admission lookup throws")
        void shouldReturnZero_whenAdmissionLookupThrows() {
            AdmissionManager admissionManager = mock(AdmissionManager.class);
            when(admissionManager.getAdmission("7", 42)).thenThrow(new RuntimeException("boom"));

            assertThat(CaseManagementEntry2Action.resolveReporterProgramTeamId(admissionManager, "7", "42"))
                    .isEqualTo("0");
        }

        @Test
        @DisplayName("should return zero when demographic number is malformed")
        void shouldReturnZero_whenDemographicNumberIsMalformed() {
            AdmissionManager admissionManager = mock(AdmissionManager.class);

            assertThat(CaseManagementEntry2Action.resolveReporterProgramTeamId(admissionManager, "7", "abc"))
                    .isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("case-management chain redirect")
    class CaseManagementChainRedirect {

        @Test
        @DisplayName("should allow list chain token")
        void shouldAllowRedirect_whenChainIsList() {
            assertThat(CaseManagementEntry2Action.isAllowedInternalRedirectChain("list")).isTrue();
        }

        @Test
        @DisplayName("should allow list chain token with whitespace")
        void shouldAllowRedirect_whenChainHasWhitespace() {
            assertThat(CaseManagementEntry2Action.isAllowedInternalRedirectChain(" list ")).isTrue();
        }

        @Test
        @DisplayName("should reject raw redirect values")
        void shouldRejectRedirect_whenChainIsRawUrl() {
            assertThat(CaseManagementEntry2Action.isAllowedInternalRedirectChain(null)).isFalse();
            assertThat(CaseManagementEntry2Action.isAllowedInternalRedirectChain("")).isFalse();
            assertThat(CaseManagementEntry2Action.isAllowedInternalRedirectChain(
                    "/carlos/provider/providercontrol.jsp?tab=main")).isFalse();
            assertThat(CaseManagementEntry2Action.isAllowedInternalRedirectChain(
                    "https://emr.example/carlos/provider/providercontrol.jsp")).isFalse();
            assertThat(CaseManagementEntry2Action.isAllowedInternalRedirectChain("//evil.example/path")).isFalse();
        }

        @Test
        @DisplayName("should include servlet context path")
        void shouldBuildRedirect_whenContextPathProvided() {
            assertThat(CaseManagementEntry2Action.caseManagementListRedirectUrl("/carlos"))
                    .isEqualTo("/carlos/CaseManagementView?method=view");
        }

        @Test
        @DisplayName("should use root path when context path is empty")
        void shouldBuildRedirect_whenContextPathEmpty() {
            assertThat(CaseManagementEntry2Action.caseManagementListRedirectUrl(""))
                    .isEqualTo("/CaseManagementView?method=view");
        }

        @Test
        @DisplayName("should reject unsafe context paths")
        void shouldRejectRedirect_whenContextPathUnsafe() {
            assertThatThrownBy(() -> CaseManagementEntry2Action.caseManagementListRedirectUrl("//evil.example"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsafe case-management redirect context path");
        }
    }
}
