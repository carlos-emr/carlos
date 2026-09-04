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
package io.github.carlos_emr.carlos.admin.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Assign Role dropdown contents, in particular the guards that keep an
 * installation from losing the ability to grant its own administrator role.
 *
 * @since 2026-09-04
 */
@DisplayName("Assignable roles")
@Tag("unit")
@Tag("admin")
class AssignableRolesUnitTest {

    private static final String ADMIN = "admin";

    private static final List<String> ALL_ROLES =
            Arrays.asList(ADMIN, "doctor", "nurse", "receptionist");

    @Nested
    @DisplayName("filter")
    class Filter {

        @Test
        @DisplayName("should keep the administrator role when multisites is disabled")
        void shouldKeepAdministratorRole_whenMultisitesDisabled() {
            // The reported alpha bug: the seeded `admin` role holds
            // `_site_access_privacy`, so a standalone install hid `admin` from itself.
            List<String> assignable = AssignableRoles.filter(
                    ALL_ROLES, true, false, ADMIN, Collections.<String>emptySet());

            assertThat(assignable).containsExactly(ADMIN, "doctor", "nurse", "receptionist");
        }

        @Test
        @DisplayName("should keep the administrator role when the caller already holds it")
        void shouldKeepAdministratorRole_whenCallerAlreadyHoldsIt() {
            List<String> assignable = AssignableRoles.filter(
                    ALL_ROLES, true, true, ADMIN, AssignableRoles.parseRoleNames("admin,doctor"));

            assertThat(assignable).contains(ADMIN);
        }

        @Test
        @DisplayName("should withhold the administrator role from a site-restricted caller in a multisite install")
        void shouldWithholdAdministratorRole_fromSiteRestrictedMultisiteCaller() {
            List<String> assignable = AssignableRoles.filter(
                    ALL_ROLES, true, true, ADMIN, AssignableRoles.parseRoleNames("receptionist"));

            assertThat(assignable)
                    .doesNotContain(ADMIN)
                    .containsExactly("doctor", "nurse", "receptionist");
        }

        @Test
        @DisplayName("should keep every role when the caller has no site access privacy")
        void shouldKeepEveryRole_whenCallerHasNoSiteAccessPrivacy() {
            List<String> assignable = AssignableRoles.filter(
                    ALL_ROLES, false, true, ADMIN, AssignableRoles.parseRoleNames("receptionist"));

            assertThat(assignable).containsExactlyElementsOf(ALL_ROLES);
        }

        @Test
        @DisplayName("should keep every role when no super root role name is configured")
        void shouldKeepEveryRole_whenNoSuperRootRoleConfigured() {
            List<String> assignable = AssignableRoles.filter(
                    ALL_ROLES, true, true, "  ", AssignableRoles.parseRoleNames("receptionist"));

            assertThat(assignable).containsExactlyElementsOf(ALL_ROLES);
        }

        @Test
        @DisplayName("should return an empty list for null role names")
        void shouldReturnEmptyList_forNullRoleNames() {
            assertThat(AssignableRoles.filter(null, true, true, ADMIN, null)).isEmpty();
        }

        @Test
        @DisplayName("should tolerate a null caller role collection")
        void shouldTolerateNullCallerRoles_withMultisiteNarrowing() {
            List<String> assignable = AssignableRoles.filter(ALL_ROLES, true, true, ADMIN, null);

            assertThat(assignable).doesNotContain(ADMIN);
        }
    }

    @Nested
    @DisplayName("parseRoleNames")
    class ParseRoleNames {

        @Test
        @DisplayName("should split and trim the comma separated session value")
        void shouldSplitAndTrimValue_fromSessionAttribute() {
            Set<String> roleNames = AssignableRoles.parseRoleNames(" admin , doctor ,,doctor ");

            assertThat(roleNames).containsExactly(ADMIN, "doctor");
        }

        @Test
        @DisplayName("should return an empty set for a null session value")
        void shouldReturnEmptySet_forNullSessionValue() {
            assertThat(AssignableRoles.parseRoleNames(null)).isEmpty();
        }
    }
}
