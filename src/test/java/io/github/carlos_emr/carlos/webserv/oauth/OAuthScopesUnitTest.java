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
package io.github.carlos_emr.carlos.webserv.oauth;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the OAuth 1.0a scope vocabulary and matching rules (issue #3083).
 */
@DisplayName("OAuthScopes vocabulary and matching")
@Tag("unit")
@Tag("security")
class OAuthScopesUnitTest {

    // The resolver consumes the servlet path info (HttpServletRequest.getPathInfo()), which the container
    // has already decoded and canonicalized to /services/<domain>/... — so these inputs are pre-normalized.
    private static final String SCHEDULE_DAY_PATH = "/services/schedule/day/2026-06-29";
    private static final String TICKLER_MINE_PATH = "/services/tickler/mine";

    @Nested
    @DisplayName("requiredScope(method, servicePath)")
    class RequiredScope {

        @Test
        @DisplayName("should require domain.read when method is a safe read on a piloted endpoint")
        void shouldRequireRead_whenSafeMethodOnPilotedEndpoint() {
            assertThat(OAuthScopes.requiredScope("GET", SCHEDULE_DAY_PATH)).isEqualTo("schedule.read");
            assertThat(OAuthScopes.requiredScope("HEAD", TICKLER_MINE_PATH)).isEqualTo("tickler.read");
            assertThat(OAuthScopes.requiredScope("OPTIONS", SCHEDULE_DAY_PATH)).isEqualTo("schedule.read");
            assertThat(OAuthScopes.requiredScope("TRACE", TICKLER_MINE_PATH)).isEqualTo("tickler.read");
        }

        @Test
        @DisplayName("should require domain.write when method mutates on a piloted endpoint")
        void shouldRequireWrite_whenMutatingMethodOnPilotedEndpoint() {
            assertThat(OAuthScopes.requiredScope("POST", SCHEDULE_DAY_PATH)).isEqualTo("schedule.write");
            assertThat(OAuthScopes.requiredScope("DELETE", TICKLER_MINE_PATH)).isEqualTo("tickler.write");
            assertThat(OAuthScopes.requiredScope("PUT", SCHEDULE_DAY_PATH)).isEqualTo("schedule.write");
        }

        @Test
        @DisplayName("should treat a null/blank method as a write for fail-safe behaviour")
        void shouldRequireWrite_whenMethodMissing() {
            assertThat(OAuthScopes.requiredScope(null, SCHEDULE_DAY_PATH)).isEqualTo("schedule.write");
            assertThat(OAuthScopes.requiredScope("", SCHEDULE_DAY_PATH)).isEqualTo("schedule.write");
            assertThat(OAuthScopes.requiredScope("   ", SCHEDULE_DAY_PATH)).isEqualTo("schedule.write");
        }

        @Test
        @DisplayName("should resolve the domain regardless of casing")
        void shouldResolveDomain_whenRootIsMixedCase() {
            assertThat(OAuthScopes.requiredScope("GET", "/services/SCHEDULE/day")).isEqualTo("schedule.read");
        }

        @Test
        @DisplayName("should resolve the domain when it is the only segment after services")
        void shouldResolveDomain_whenDomainIsLastSegment() {
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler")).isEqualTo("tickler.write");
        }

        @Test
        @DisplayName("should require no scope for the explicitly exempt oauth root")
        void shouldRequireNoScope_forExemptOauthRoot() {
            // OAuthStatusService (/oauth/info) describes the token's own provider; any valid token may call it.
            assertThat(OAuthScopes.requiredScope("GET", "/services/oauth/info"))
                    .isEqualTo(OAuthScopes.NO_SCOPE_REQUIRED);
            assertThat(OAuthScopes.requiredScope("GET", "/services/OAuth/info.json"))
                    .isEqualTo(OAuthScopes.NO_SCOPE_REQUIRED);
        }

        @Test
        @DisplayName("should fail closed for a root that is in neither map")
        void shouldRequireUnmappedEndpoint_forUnknownRoot() {
            // #4419: an unknown root used to need no scope, so a newly published service was open to any token.
            assertThat(OAuthScopes.requiredScope("POST", "/services/madeup/x"))
                    .isEqualTo(OAuthScopes.UNMAPPED_ENDPOINT);
            assertThat(OAuthScopes.requiredScope("GET", "/services/madeup"))
                    .isEqualTo(OAuthScopes.UNMAPPED_ENDPOINT);
        }

        @Test
        @DisplayName("should strip a .json or .xml extension mapping from the last segment, as CXF does")
        void shouldStripExtensionMapping_fromLastSegment() {
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler.json")).isEqualTo("tickler.write");
            assertThat(OAuthScopes.requiredScope("GET", "/services/demographics.xml")).isEqualTo("demographic.read");
            assertThat(OAuthScopes.requiredScope("GET", "/services/demographics/1.json")).isEqualTo("demographic.read");
        }

        @Test
        @DisplayName("should not strip an extension from an interior segment")
        void shouldKeepInteriorSegment_withExtensionLikeName() {
            // CXF strips only the end of the path, so tickler.json/search is not routed to /tickler/search.
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler.json/search"))
                    .isEqualTo(OAuthScopes.UNMAPPED_ENDPOINT);
        }

        @Test
        @DisplayName("should require no scope when the path has no services segment")
        void shouldRequireNoScope_whenNoServicesSegment() {
            assertThat(OAuthScopes.requiredScope("GET", "/oauth/initiate"))
                    .isEqualTo(OAuthScopes.NO_SCOPE_REQUIRED);
            assertThat(OAuthScopes.requiredScope("GET", null))
                    .isEqualTo(OAuthScopes.NO_SCOPE_REQUIRED);
        }

        @Test
        @DisplayName("should fail closed when nothing follows the services prefix")
        void shouldRequireUnmappedEndpoint_whenNothingAfterServices() {
            // e.g. /ws/services?_wadl: no root, so no scope decision (#4419 review).
            assertThat(OAuthScopes.requiredScope("GET", "/services/"))
                    .isEqualTo(OAuthScopes.UNMAPPED_ENDPOINT);
            assertThat(OAuthScopes.requiredScope("GET", "/services"))
                    .isEqualTo(OAuthScopes.UNMAPPED_ENDPOINT);
        }

        @Test
        @DisplayName("should not strip an extension in another case, as CXF does not")
        void shouldKeepSuffix_whenExtensionCaseDiffers() {
            // CXF's endsWith(".json") is case-sensitive, so tickler/search.JSON is not routed to search.
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler/search.JSON"))
                    .isEqualTo("tickler.write");
        }

        @Test
        @DisplayName("should classify an extension-mapped read POST as a read when no matrix parameter is present")
        void shouldRequireRead_forExtensionMappedReadWithoutMatrixParameters() {
            // CXF strips .json here and routes to the search read operation.
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler/search.json", false))
                    .isEqualTo("tickler.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler/search.JSON", false))
                    .isEqualTo("tickler.write");
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler.json", false))
                    .isEqualTo("tickler.write");
        }

        @Test
        @DisplayName("should classify a POST as a read only when both path forms are read operations")
        void shouldRequireWrite_whenOnlyStrippedFormIsRead() {
            // With a matrix parameter (the 2-argument form assumes one may be present) CXF may route either
            // tickler/search.json or tickler/search, so the stricter answer wins.
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler/search.json"))
                    .isEqualTo("tickler.write");
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler/search.json", true))
                    .isEqualTo("tickler.write");
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler/search"))
                    .isEqualTo("tickler.read");
        }
    }

    @Nested
    @DisplayName("requiredScope — full service map and per-endpoint read/write")
    class FullMapClassification {

        @Test
        @DisplayName("should map newly-covered services to their domain by HTTP method")
        void shouldMapDomain_forNewlyCoveredServices() {
            assertThat(OAuthScopes.requiredScope("GET", "/services/demographics/1")).isEqualTo("demographic.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/demographics")).isEqualTo("demographic.write");
            assertThat(OAuthScopes.requiredScope("GET", "/services/labs/123")).isEqualTo("lab.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/allergies")).isEqualTo("allergy.write");
        }

        @Test
        @DisplayName("should share one domain across related path roots")
        void shouldShareDomain_acrossRelatedRoots() {
            // rx + rxlookup -> rx ; reporting + reportbytemplate -> report ; eform + eforms -> eform
            assertThat(OAuthScopes.requiredScope("POST", "/services/rx/new")).isEqualTo("rx.write");
            assertThat(OAuthScopes.requiredScope("POST", "/services/rxlookup/parse")).isEqualTo("rx.read");
            assertThat(OAuthScopes.requiredScope("GET", "/services/reportbytemplate/1")).isEqualTo("report.read");
            assertThat(OAuthScopes.requiredScope("GET", "/services/eforms/1")).isEqualTo("eform.read");
        }

        @Test
        @DisplayName("should classify a read-only POST as a read on a mixed service")
        void shouldRequireRead_whenNonSafeMethodHitsReadOperation() {
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler/search")).isEqualTo("tickler.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/schedule/getAppointment"))
                    .isEqualTo("schedule.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/demographics/search"))
                    .isEqualTo("demographic.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/notes/123/all")).isEqualTo("note.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/consults/searchRequests"))
                    .isEqualTo("consultation.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/reporting/preventionReport/getReport/5"))
                    .isEqualTo("report.read");
        }

        @Test
        @DisplayName("should keep a mutating POST as a write on a mixed service")
        void shouldRequireWrite_whenNonSafeMethodHitsMutation() {
            assertThat(OAuthScopes.requiredScope("POST", "/services/schedule/add")).isEqualTo("schedule.write");
            assertThat(OAuthScopes.requiredScope("POST", "/services/tickler/add")).isEqualTo("tickler.write");
            assertThat(OAuthScopes.requiredScope("POST", "/services/notes/123/save")).isEqualTo("note.write");
            assertThat(OAuthScopes.requiredScope("POST", "/services/consults/saveRequest"))
                    .isEqualTo("consultation.write");
        }

        @Test
        @DisplayName("should treat a non-safe method as a read on a wholly read-only service")
        void shouldRequireRead_whenServiceIsReadOnlyOnNonSafeMethods() {
            assertThat(OAuthScopes.requiredScope("POST", "/services/measurements/123"))
                    .isEqualTo("measurement.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/recordUX/searchTemplates"))
                    .isEqualTo("recordux.read");
            assertThat(OAuthScopes.requiredScope("POST", "/services/patientDetailStatusService/validateHC"))
                    .isEqualTo("patientstatus.read");
        }

        @Test
        @DisplayName("should not let a path-parameter value equal to a read marker escalate a write")
        void shouldRequireWrite_whenParamValueEqualsReadMarker() {
            // saveProviderSettings is POST /providerService/settings/{providerNo}/save; providerNo is a String,
            // so providerNo="search" must NOT match the read template providerService/providers/search.
            assertThat(OAuthScopes.requiredScope("POST", "/services/providerService/settings/search/save"))
                    .isEqualTo("provider.write");
            // deleteDemographicData is DELETE /demographics/{dataId}; a DELETE is never a read override.
            assertThat(OAuthScopes.requiredScope("DELETE", "/services/demographics/search"))
                    .isEqualTo("demographic.write");
            // the genuine read endpoint still resolves to read
            assertThat(OAuthScopes.requiredScope("POST", "/services/providerService/providers/search"))
                    .isEqualTo("provider.read");
        }

        @Test
        @DisplayName("should treat non-POST and missing methods as writes even on a read-only service")
        void shouldRequireWrite_whenNonPostMethodOnReadOnlyService() {
            assertThat(OAuthScopes.requiredScope("DELETE", "/services/measurements/123"))
                    .isEqualTo("measurement.write");
            assertThat(OAuthScopes.requiredScope(null, "/services/measurements/123"))
                    .isEqualTo("measurement.write");
            assertThat(OAuthScopes.requiredScope("   ", "/services/measurements/123"))
                    .isEqualTo("measurement.write");
        }
    }

    @Nested
    @DisplayName("isKnownScope(scope)")
    class IsKnownScope {

        @Test
        @DisplayName("should accept a recognised read/write scope case-insensitively")
        void shouldAccept_forRecognisedScope() {
            assertThat(OAuthScopes.isKnownScope("schedule.read")).isTrue();
            assertThat(OAuthScopes.isKnownScope("tickler.write")).isTrue();
            assertThat(OAuthScopes.isKnownScope("  SCHEDULE.READ  ")).isTrue();
            // newly-mapped domains are part of the /initiate vocabulary too
            assertThat(OAuthScopes.isKnownScope("demographic.read")).isTrue();
            assertThat(OAuthScopes.isKnownScope("rx.write")).isTrue();
            assertThat(OAuthScopes.isKnownScope("report.read")).isTrue();
        }

        @Test
        @DisplayName("should reject an unknown, empty, or null scope")
        void shouldReject_forUnknownScope() {
            assertThat(OAuthScopes.isKnownScope("totally_bogus_zzz")).isFalse();
            assertThat(OAuthScopes.isKnownScope("schedule")).isFalse();
            assertThat(OAuthScopes.isKnownScope("")).isFalse();
            assertThat(OAuthScopes.isKnownScope(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isSatisfiedBy(required, granted)")
    class IsSatisfiedBy {

        @Test
        @DisplayName("should be satisfied when no scope is required")
        void shouldBeSatisfied_whenNoScopeRequired() {
            assertThat(OAuthScopes.isSatisfiedBy(OAuthScopes.NO_SCOPE_REQUIRED, List.of())).isTrue();
        }

        @Test
        @DisplayName("should never be satisfied for an unmapped endpoint")
        void shouldNotBeSatisfied_forUnmappedEndpoint() {
            assertThat(OAuthScopes.isSatisfiedBy(OAuthScopes.UNMAPPED_ENDPOINT,
                    List.of("unmapped endpoint", "unmapped", "endpoint", "tickler.write"))).isFalse();
        }

        @Test
        @DisplayName("should be satisfied by an exact scope grant")
        void shouldBeSatisfied_byExactGrant() {
            assertThat(OAuthScopes.isSatisfiedBy("schedule.read", List.of("schedule.read"))).isTrue();
        }

        @Test
        @DisplayName("should let a write grant satisfy the matching read requirement")
        void shouldBeSatisfied_whenWriteGrantCoversRead() {
            assertThat(OAuthScopes.isSatisfiedBy("schedule.read", List.of("schedule.write"))).isTrue();
        }

        @Test
        @DisplayName("should not let a read grant satisfy a write requirement")
        void shouldNotBeSatisfied_whenReadGrantForWriteRequirement() {
            assertThat(OAuthScopes.isSatisfiedBy("schedule.write", List.of("schedule.read"))).isFalse();
        }

        @Test
        @DisplayName("should not be satisfied by a scope for a different domain")
        void shouldNotBeSatisfied_byDifferentDomain() {
            assertThat(OAuthScopes.isSatisfiedBy("schedule.read", List.of("tickler.write"))).isFalse();
        }

        @Test
        @DisplayName("should not be satisfied when no scopes are granted")
        void shouldNotBeSatisfied_whenNoScopesGranted() {
            assertThat(OAuthScopes.isSatisfiedBy("schedule.read", List.of())).isFalse();
            assertThat(OAuthScopes.isSatisfiedBy("schedule.read", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("parseScopeString")
    class ParseScopeString {

        @Test
        @DisplayName("should decode a percent-encoded multi-scope value before splitting")
        void shouldSplitScopes_fromPercentEncodedValue() {
            assertThat(OAuthScopes.parseScopeString("demographic.read%20provider.read"))
                    .containsExactly("demographic.read", "provider.read");
        }

        @Test
        @DisplayName("should drop empty tokens from surrounding and repeated whitespace")
        void shouldDropEmptyTokens_withStrayWhitespace() {
            assertThat(OAuthScopes.parseScopeString("  demographic.read \t provider.write  "))
                    .containsExactly("demographic.read", "provider.write");
        }

        @Test
        @DisplayName("should return no scopes for a null or blank value")
        void shouldReturnEmpty_forNullOrBlank() {
            assertThat(OAuthScopes.parseScopeString(null)).isEmpty();
            assertThat(OAuthScopes.parseScopeString("   ")).isEmpty();
        }

        @Test
        @DisplayName("should keep a malformed escape as written")
        void shouldKeepMalformedEscape_asWritten() {
            assertThat(OAuthScopes.parseScopeString("demographic.read%2")).containsExactly("demographic.read%2");
        }
    }

    @Nested
    @DisplayName("isAlwaysBlocked(method, servicePath)")
    class IsAlwaysBlocked {

        @Test
        @DisplayName("should block the whole scheduled-job service for any method")
        void shouldBlockJobs_forAnyMethod() {
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/jobs/all", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("POST", "/services/jobs/saveJob", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/jobs", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/JOBS/all.json", false)).isTrue();
        }

        @Test
        @DisplayName("should not offer job scopes at /initiate once every job endpoint is blocked")
        void shouldDropJobScopes_fromVocabulary() {
            assertThat(OAuthScopes.isKnownScope("job.read")).isFalse();
            assertThat(OAuthScopes.isKnownScope("job.write")).isFalse();
            assertThat(OAuthScopes.isKnownScope("tickler.read")).isTrue();
        }

        @Test
        @DisplayName("should block the account-rights lookups but not the rest of persona")
        void shouldBlockRightsLookups_onPersona() {
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/persona/rights", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/persona/hasRight", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("POST", "/services/persona/hasRights", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/persona/navbar", false)).isFalse();
            assertThat(OAuthScopes.isAlwaysBlocked("POST", "/services/persona/preferences", false)).isFalse();
        }

        @Test
        @DisplayName("should block record merges on mutating methods only")
        void shouldBlockMergeWrites_butAllowMergeReads() {
            assertThat(OAuthScopes.isAlwaysBlocked("PUT", "/services/demographics/merge/", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("DELETE", "/services/demographics/merge", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/demographics/merge/42", false)).isFalse();
            assertThat(OAuthScopes.isAlwaysBlocked("DELETE", "/services/demographics/42", false)).isFalse();
        }

        @Test
        @DisplayName("should block provider settings writes and the recently-viewed lists")
        void shouldBlockSettingsSave_andRecentlyViewed() {
            assertThat(OAuthScopes.isAlwaysBlocked("POST", "/services/providerService/settings/999998/save", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/providerService/getRecentDemographicsViewed", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("GET",
                    "/services/providerService/getRecentDemographicsViewedAfterDateIncluded", false)).isTrue();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/providerService/settings/get", false)).isFalse();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/providerService/provider/me", false)).isFalse();
        }

        @Test
        @DisplayName("should block a matrix-parameter spelling when either path form is blocked")
        void shouldBlock_whenEitherPathFormMatchesUnderMatrixParameters() {
            // With ";x=1" CXF may route "rights.json" unstripped (no match) or stripped (blocked).
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/persona/rights.json", true)).isTrue();
        }

        @Test
        @DisplayName("should not block ordinary data endpoints or non-service paths")
        void shouldNotBlock_ordinaryEndpoints() {
            assertThat(OAuthScopes.isAlwaysBlocked("GET", TICKLER_MINE_PATH, false)).isFalse();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/services/oauth/info", false)).isFalse();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", "/rs/demographics/1", false)).isFalse();
            assertThat(OAuthScopes.isAlwaysBlocked("GET", null, false)).isFalse();
        }
    }

    /**
     * The REST calls the legacy patient-engagement integration makes (the rest of its calls are SOAP).
     * They must work in legacy-restricted mode with no scopes, must never be always-blocked, and must work
     * under enforcement with the two scopes they need.
     */
    private static final List<String[]> LEGACY_INTEGRATION_CALLS = List.of(
            new String[] {"POST", "/services/demographics/"},
            new String[] {"PUT", "/services/demographics/"},
            new String[] {"GET", "/services/demographics/12345"},
            new String[] {"POST", "/services/document/saveDocumentToDemographic/"});
    private static final List<String> LEGACY_INTEGRATION_SCOPES = List.of("demographic.write", "document.write");

    @Nested
    @DisplayName("isLegacyRestrictedAllowed(method, servicePath)")
    class IsLegacyRestrictedAllowed {

        @Test
        @DisplayName("should admit every legacy integration call with no scopes at all")
        void shouldAdmitLegacyIntegrationCalls_withoutScopes() {
            for (String[] call : LEGACY_INTEGRATION_CALLS) {
                assertThat(OAuthScopes.isLegacyRestrictedAllowed(call[0], call[1], false))
                        .as("%s %s", call[0], call[1]).isTrue();
                assertThat(OAuthScopes.isAlwaysBlocked(call[0], call[1], false))
                        .as("%s %s must not be blocked", call[0], call[1]).isFalse();
            }
        }

        @Test
        @DisplayName("should satisfy every legacy integration call under enforcement with demographic.write and document.write")
        void shouldSatisfyLegacyIntegrationCalls_withTheirScopes() {
            for (String[] call : LEGACY_INTEGRATION_CALLS) {
                String required = OAuthScopes.requiredScope(call[0], call[1], false);
                assertThat(OAuthScopes.isSatisfiedBy(required, LEGACY_INTEGRATION_SCOPES))
                        .as("%s %s needs %s", call[0], call[1], required).isTrue();
            }
            assertThat(OAuthScopes.isKnownScope("demographic.write")).isTrue();
            assertThat(OAuthScopes.isKnownScope("document.write")).isTrue();
        }

        @Test
        @DisplayName("should admit the extension-mapped and case-varied spellings CXF routes the same way")
        void shouldAdmitLegacyIntegrationCalls_inRoutedSpellings() {
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/services/demographics/12345.json", false)).isTrue();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("post", "/services/Demographics", false)).isTrue();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("POST", "/services/document/saveDocumentToDemographic.json", false)).isTrue();
        }

        @Test
        @DisplayName("should refuse everything else on the demographics and document services")
        void shouldRefuseOtherOperations_onAllowedRoots() {
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/services/demographics/", false)).isFalse();   // list all
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/services/demographics/quickSearch", false)).isFalse();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("POST", "/services/demographics/search", false)).isFalse();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("DELETE", "/services/demographics/12345", false)).isFalse();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/services/demographics/basic/12345", false)).isFalse();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/services/document/saveDocumentToDemographic", false)).isFalse();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("POST", "/services/document/uploadPendingDocuments", false)).isFalse();
        }

        @Test
        @DisplayName("should refuse every other service, the bare services root, and null methods")
        void shouldRefuse_otherServices() {
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", TICKLER_MINE_PATH, false)).isFalse();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", SCHEDULE_DAY_PATH, false)).isFalse();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/services", false)).isFalse();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed(null, "/services/demographics/", false)).isFalse();
        }

        @Test
        @DisplayName("should keep the scope-exempt oauth root and non-service paths open")
        void shouldAdmit_exemptRootAndNonServicePaths() {
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/services/oauth/info", false)).isTrue();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/rs/demographics/1", false)).isTrue();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", null, false)).isTrue();
        }

        @Test
        @DisplayName("should require both path forms to be allowed under a matrix parameter")
        void shouldRequireBothForms_underMatrixParameters() {
            // Stripped, "12345" is the allowed read; unstripped, "12345.json" is not a number.
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/services/demographics/12345.json", true)).isFalse();
            assertThat(OAuthScopes.isLegacyRestrictedAllowed("GET", "/services/demographics/12345", true)).isTrue();
        }
    }
}
