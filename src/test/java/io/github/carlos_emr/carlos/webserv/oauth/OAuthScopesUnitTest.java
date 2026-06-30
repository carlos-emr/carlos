/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
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

    private static final String SCHEDULE_DAY_URI = "/carlos/ws/services/schedule/day/2026-06-29";
    private static final String TICKLER_MINE_URI = "/carlos/ws/services/tickler/mine";

    @Nested
    @DisplayName("requiredScope(method, uri)")
    class RequiredScope {

        @Test
        @DisplayName("should require domain.read when method is a safe read on a piloted endpoint")
        void shouldRequireRead_whenSafeMethodOnPilotedEndpoint() {
            assertThat(OAuthScopes.requiredScope("GET", SCHEDULE_DAY_URI)).isEqualTo("schedule.read");
            assertThat(OAuthScopes.requiredScope("HEAD", TICKLER_MINE_URI)).isEqualTo("tickler.read");
        }

        @Test
        @DisplayName("should require domain.write when method mutates on a piloted endpoint")
        void shouldRequireWrite_whenMutatingMethodOnPilotedEndpoint() {
            assertThat(OAuthScopes.requiredScope("POST", SCHEDULE_DAY_URI)).isEqualTo("schedule.write");
            assertThat(OAuthScopes.requiredScope("DELETE", TICKLER_MINE_URI)).isEqualTo("tickler.write");
            assertThat(OAuthScopes.requiredScope("PUT", SCHEDULE_DAY_URI)).isEqualTo("schedule.write");
        }

        @Test
        @DisplayName("should treat a null/blank method as a write for fail-safe behaviour")
        void shouldRequireWrite_whenMethodMissing() {
            assertThat(OAuthScopes.requiredScope(null, SCHEDULE_DAY_URI)).isEqualTo("schedule.write");
        }

        @Test
        @DisplayName("should require no scope for an endpoint outside the pilot")
        void shouldRequireNoScope_forUnpilotedDomain() {
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/services/demographic/1"))
                    .isEqualTo(OAuthScopes.NO_SCOPE_REQUIRED);
        }

        @Test
        @DisplayName("should require no scope when the uri has no services segment")
        void shouldRequireNoScope_whenNoServicesSegment() {
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/oauth/initiate"))
                    .isEqualTo(OAuthScopes.NO_SCOPE_REQUIRED);
            assertThat(OAuthScopes.requiredScope("GET", null))
                    .isEqualTo(OAuthScopes.NO_SCOPE_REQUIRED);
        }

        @Test
        @DisplayName("should anchor on the ws/services mount and ignore an earlier services segment")
        void shouldResolveDomain_whenEarlierServicesSegmentPresent() {
            // The marker is the full /ws/services/ mount, so a decoy earlier 'services' segment must not
            // misanchor the root and make the piloted endpoint look unpiloted.
            assertThat(OAuthScopes.requiredScope("GET", "/services/decoy/ws/services/schedule/day"))
                    .isEqualTo("schedule.read");
        }

        @Test
        @DisplayName("should ignore a trailing query string when resolving the domain")
        void shouldResolveDomain_whenQueryStringPresent() {
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/services/schedule?date=today"))
                    .isEqualTo("schedule.read");
        }

        @Test
        @DisplayName("should resolve the domain when the path root is percent-encoded")
        void shouldResolveDomain_whenPercentEncoded() {
            // %73 decodes to 's' -> the request still routes to the schedule resource and must be enforced
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/services/%73chedule/day/2026-06-29"))
                    .isEqualTo("schedule.read");
        }

        @Test
        @DisplayName("should strip matrix parameters from the path root")
        void shouldResolveDomain_whenMatrixParameterPresent() {
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/services/schedule;v=1/day"))
                    .isEqualTo("schedule.read");
        }

        @Test
        @DisplayName("should strip a percent-encoded matrix delimiter from the path root")
        void shouldResolveDomain_whenMatrixDelimiterEncoded() {
            // %3B decodes to ';' -> must still be treated as a matrix delimiter, not part of the domain
            assertThat(OAuthScopes.requiredScope("POST", "/carlos/ws/services/tickler%3Bx=1/add"))
                    .isEqualTo("tickler.write");
        }

        @Test
        @DisplayName("should collapse a leading dot segment when resolving the domain")
        void shouldResolveDomain_whenLeadingDotSegment() {
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/services/./schedule/day"))
                    .isEqualTo("schedule.read");
        }

        @Test
        @DisplayName("should resolve dot-dot segments to the routed domain")
        void shouldResolveDomain_whenDotDotSegmentPresent() {
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/services/other/../schedule/day"))
                    .isEqualTo("schedule.read");
        }

        @Test
        @DisplayName("should not let a leading dot-dot segment hide a piloted domain")
        void shouldResolveDomain_whenLeadingDotDotSegment() {
            // A leading '..' must not pop the pilot domain off and make the request look unpiloted.
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/services/../schedule/day"))
                    .isEqualTo("schedule.read");
            assertThat(OAuthScopes.requiredScope("POST", "/carlos/ws/services/../../tickler/add"))
                    .isEqualTo("tickler.write");
        }

        @Test
        @DisplayName("should treat an encoded slash as a path separator when resolving the domain")
        void shouldResolveDomain_whenEncodedSlashPresent() {
            // %2F decodes to '/'; a decoded slash must be re-split so it cannot mask the pilot root.
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/services/schedule%2Fday"))
                    .isEqualTo("schedule.read");
            // %2e%2e%2f decodes to '../' -> must still resolve through the pilot root, not bypass it.
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/services/%2e%2e%2fschedule/day"))
                    .isEqualTo("schedule.read");
        }

        @Test
        @DisplayName("should resolve the domain when the mount prefix is percent-encoded")
        void shouldResolveDomain_whenEncodedMountPrefix() {
            // %73 decodes to 's' -> /ws/%73ervices/ is the routed /ws/services/ mount and must be enforced.
            assertThat(OAuthScopes.requiredScope("GET", "/carlos/ws/%73ervices/schedule/day"))
                    .isEqualTo("schedule.read");
            // An encoded slash in the mount prefix must also map back to the mount.
            assertThat(OAuthScopes.requiredScope("POST", "/carlos/ws%2Fservices/tickler/add"))
                    .isEqualTo("tickler.write");
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
}
