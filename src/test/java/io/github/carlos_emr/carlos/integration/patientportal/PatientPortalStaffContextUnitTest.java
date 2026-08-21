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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The caller identity that the portal authorizes every call against.
 *
 * <p>These guards had no coverage at all, which meant the header-integrity checks could have been
 * deleted with the whole suite still green. They are not hygiene: each one turns a value that would
 * corrupt the {@code X-CARLOS-*} headers into a CARLOS-side failure.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PatientPortalStaffContext")
class PatientPortalStaffContextUnitTest {

    private static final String PROVIDER_ID = "999998";
    private static final String PROVIDER_NAME = "Dr Example";
    private static final Set<String> ONE_PERMISSION =
            Set.of(PatientPortalStaffContext.PERMISSION_INVITE_MANAGE);

    @Nested
    @DisplayName("header integrity")
    class HeaderIntegrity {

        /**
         * {@code permissionHeaderValue} joins on commas, so a permission containing one would
         * arrive at the portal as two claimed permissions — privilege escalation through a
         * malformed CARLOS value rather than through the portal.
         */
        @Test
        @DisplayName("should reject a permission containing a comma")
        void shouldThrow_whenPermissionContainsAComma() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalStaffContext(
                                            PROVIDER_ID,
                                            PROVIDER_NAME,
                                            Set.of("portal.invite.manage,portal.secret.manage")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("comma");
        }

        /**
         * A newline in any of the three values would let a caller append headers of its own,
         * including a second X-CARLOS-Permissions claiming privileges the provider does not hold.
         * An earlier revision guarded commas in permissions and wrote provider id and name raw.
         */
        @Test
        @DisplayName("should reject a newline in the provider id")
        void shouldThrow_whenProviderIdContainsANewline() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalStaffContext(
                                            "999998\r\nX-CARLOS-Permissions: portal.secret.manage",
                                            PROVIDER_NAME,
                                            ONE_PERMISSION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("control characters");
        }

        @Test
        @DisplayName("should reject a newline in the provider name")
        void shouldThrow_whenProviderNameContainsANewline() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalStaffContext(
                                            PROVIDER_ID, "Dr\nExample", ONE_PERMISSION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("control characters");
        }

        @Test
        @DisplayName("should reject a newline inside a permission")
        void shouldThrow_whenPermissionContainsANewline() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalStaffContext(
                                            PROVIDER_ID,
                                            PROVIDER_NAME,
                                            Set.of("portal.invite.manage\nX-Evil: 1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("control characters");
        }
    }

    @Nested
    @DisplayName("identity")
    class Identity {

        @Test
        @DisplayName("should reject a missing provider id")
        void shouldThrow_whenProviderIdIsBlank() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalStaffContext(
                                            "   ", PROVIDER_NAME, ONE_PERMISSION))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    new PatientPortalStaffContext(
                                            null, PROVIDER_NAME, ONE_PERMISSION))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject a missing provider name")
        void shouldThrow_whenProviderNameIsBlank() {
            assertThatThrownBy(
                            () -> new PatientPortalStaffContext(PROVIDER_ID, "  ", ONE_PERMISSION))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should trim surrounding whitespace from the identity")
        void shouldTrimIdentity_whenValuesArePadded() {
            PatientPortalStaffContext staff =
                    new PatientPortalStaffContext("  999998  ", "  Dr Example  ", ONE_PERMISSION);

            assertThat(staff.providerId()).isEqualTo(PROVIDER_ID);
            assertThat(staff.providerName()).isEqualTo(PROVIDER_NAME);
        }
    }

    @Nested
    @DisplayName("permissions")
    class Permissions {

        @Test
        @DisplayName("should reject an empty permission set")
        void shouldThrow_whenNoPermissionIsHeld() {
            assertThatThrownBy(
                            () ->
                                    new PatientPortalStaffContext(
                                            PROVIDER_ID, PROVIDER_NAME, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject more permissions than the portal accepts")
        void shouldThrow_whenPermissionCountExceedsThePortalLimit() {
            Set<String> tooMany = new LinkedHashSet<>();
            for (int index = 0; index <= PatientPortalStaffContext.MAX_PERMISSION_COUNT; index++) {
                tooMany.add("portal.permission." + index);
            }

            assertThatThrownBy(
                            () ->
                                    new PatientPortalStaffContext(
                                            PROVIDER_ID, PROVIDER_NAME, tooMany))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at most");
        }

        @Test
        @DisplayName("should reject a permission longer than the portal accepts")
        void shouldThrow_whenPermissionExceedsTheLengthLimit() {
            String tooLong = "p".repeat(PatientPortalStaffContext.MAX_PERMISSION_LENGTH + 1);

            assertThatThrownBy(
                            () ->
                                    new PatientPortalStaffContext(
                                            PROVIDER_ID, PROVIDER_NAME, Set.of(tooLong)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("should strip whitespace so the portal parser sees a clean permission")
        void shouldStripPermissions_whenValuesArePadded() {
            PatientPortalStaffContext staff =
                    new PatientPortalStaffContext(
                            PROVIDER_ID, PROVIDER_NAME, Set.of("  portal.invite.manage  "));

            assertThat(staff.permissionHeaderValue()).isEqualTo("portal.invite.manage");
        }

        @Test
        @DisplayName("should render permissions in a stable order")
        void shouldRenderPermissions_inSortedOrder() {
            PatientPortalStaffContext staff =
                    new PatientPortalStaffContext(
                            PROVIDER_ID,
                            PROVIDER_NAME,
                            Set.of(
                                    PatientPortalStaffContext.PERMISSION_SECRET_MANAGE,
                                    PatientPortalStaffContext.PERMISSION_ACCOUNT_UNLOCK,
                                    PatientPortalStaffContext.PERMISSION_INVITE_MANAGE));

            assertThat(staff.permissionHeaderValue())
                    .isEqualTo("portal.account.unlock,portal.invite.manage,portal.secret.manage");
        }

        @Test
        @DisplayName("should not expose its permission set to later mutation")
        void shouldCopyPermissions_whenTheCallerRetainsTheSet() {
            Set<String> mutable = new LinkedHashSet<>(ONE_PERMISSION);
            PatientPortalStaffContext staff =
                    new PatientPortalStaffContext(PROVIDER_ID, PROVIDER_NAME, mutable);

            mutable.add(PatientPortalStaffContext.PERMISSION_SECRET_MANAGE);

            assertThat(staff.permissions()).hasSize(1);
            assertThat(staff.permissionHeaderValue()).isEqualTo("portal.invite.manage");
        }

        /**
         * The outbound half of the same property, which nothing pinned.
         *
         * <p>The inbound test above shows the caller's set is copied. Static analysis flags the
         * generated accessor as exposing internal representation, which would be true if the
         * compact constructor kept a mutable set. It assigns {@code Set.copyOf}, so a caller that
         * reaches for the permission set cannot add a privilege the provider does not hold.
         */
        @Test
        @DisplayName("should refuse mutation through the accessor it hands out")
        void shouldExposeAnUnmodifiableSet_toEveryCaller() {
            PatientPortalStaffContext staff =
                    new PatientPortalStaffContext(PROVIDER_ID, PROVIDER_NAME, ONE_PERMISSION);

            Set<String> exposed = staff.permissions();

            assertThatThrownBy(
                            () -> exposed.add(PatientPortalStaffContext.PERMISSION_SECRET_MANAGE))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> exposed.clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(staff.permissions()).containsExactly(
                    PatientPortalStaffContext.PERMISSION_INVITE_MANAGE);
        }
    }
}
