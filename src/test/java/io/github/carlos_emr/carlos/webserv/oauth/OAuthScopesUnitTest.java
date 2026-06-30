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
        @DisplayName("should require no scope for an endpoint outside the pilot")
        void shouldRequireNoScope_forUnpilotedDomain() {
            assertThat(OAuthScopes.requiredScope("GET", "/services/demographic/1"))
                    .isEqualTo(OAuthScopes.NO_SCOPE_REQUIRED);
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
        @DisplayName("should require no scope when nothing follows the services segment")
        void shouldRequireNoScope_whenNothingAfterServices() {
            assertThat(OAuthScopes.requiredScope("GET", "/services/"))
                    .isEqualTo(OAuthScopes.NO_SCOPE_REQUIRED);
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
