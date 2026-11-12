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
package io.github.carlos_emr.carlos.util;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how {@link OscarRoleObjectPrivilege#checkPrivilege(String, Properties, List)} reads a login's
 * role rows, the way {@code EctDisplayEpisode2Action} uses it on {@code _newCasemgmt.episode}, the
 * second of the chart Episode module's two gates (the first, {@code _episode}, is
 * {@code SecurityManager.hasReadAccess}; see {@code SecurityManagerHasReadAccessUnitTest}).
 *
 * <p>Issue #4566 aligned the devcontainer with the Flyway seeds, which give the {@code doctor} role a
 * bare {@code o} (no rights) on both objects and the {@code admin} role no row, so the seeded login
 * (doctor + admin + its provider number) does not see the module. The Playwright checks that drive
 * the module grant both objects to the login's provider number for their run; these cases pin both
 * halves here: the seeded denial holds, and a provider-number grant overrides it at the same priority
 * whichever order the rows come back in.
 *
 * @since 2026-10-14
 */
@DisplayName("OscarRoleObjectPrivilege.checkPrivilege role resolution")
@Tag("unit")
@Tag("security")
class OscarRoleObjectPrivilegeCheckPrivilegeUnitTest {

    /** The session role list of the seeded test login: userrole + "," + provider number. */
    private static final String SEEDED_LOGIN = "doctor,admin,999998";

    private static Properties privileges(String... roleAndPrivilege) {
        Properties properties = new Properties();
        for (int i = 0; i < roleAndPrivilege.length; i += 2) {
            properties.setProperty(roleAndPrivilege[i], roleAndPrivilege[i + 1]);
        }
        return properties;
    }

    @Test
    @DisplayName("should deny read when the login's only row is the seeded doctor o")
    void shouldDenyRead_whenSeededDoctorRowIsBareO() {
        boolean granted = OscarRoleObjectPrivilege.checkPrivilege(SEEDED_LOGIN, privileges("doctor", "o"), List.of("doctor"));

        assertThat(granted).isFalse();
    }

    @Test
    @DisplayName("should grant read when the doctor role holds x, as the pre-#4566 devcontainer did")
    void shouldGrantRead_whenDoctorRoleHoldsX() {
        boolean granted = OscarRoleObjectPrivilege.checkPrivilege(SEEDED_LOGIN, privileges("doctor", "x"), List.of("doctor"));

        assertThat(granted).isTrue();
    }

    @Test
    @DisplayName("should grant read when the provider number holds x despite the doctor o, in either row order")
    void shouldGrantRead_whenProviderNumberRowOverridesRoleDenial() {
        Properties rows = privileges("doctor", "o", "999998", "x");

        assertThat(OscarRoleObjectPrivilege.checkPrivilege(SEEDED_LOGIN, rows, List.of("doctor", "999998"))).isTrue();
        assertThat(OscarRoleObjectPrivilege.checkPrivilege(SEEDED_LOGIN, rows, List.of("999998", "doctor"))).isTrue();
    }

    @Test
    @DisplayName("should deny read when only a role the login does not hold grants the object")
    void shouldDenyRead_whenOnlyAnotherRoleGrantsTheObject() {
        Properties rows = privileges("doctor", "o", "nurse", "x", "999997", "x");

        boolean granted = OscarRoleObjectPrivilege.checkPrivilege(SEEDED_LOGIN, rows, List.of("doctor", "nurse", "999997"));

        assertThat(granted).isFalse();
    }

    @Test
    @DisplayName("should deny read when no row names the object for any of the login's roles")
    void shouldDenyRead_whenObjectHasNoRows() {
        boolean granted = OscarRoleObjectPrivilege.checkPrivilege(SEEDED_LOGIN, privileges(), List.of());

        assertThat(granted).isFalse();
    }
}
