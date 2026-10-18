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
package io.github.carlos_emr.carlos.services.security;

import io.github.carlos_emr.carlos.daos.security.SecobjprivilegeDao;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how {@link SecurityManager#hasReadAccess(String, String)} reads the rows of a login's roles,
 * the way {@code EctDisplayAction} uses it to gate each chart navbar module on {@code "_" + cmd}.
 *
 * <p>The chart's Episode module passes this gate on {@code _episode} before
 * {@code EctDisplayEpisode2Action} checks {@code _newCasemgmt.episode}. Issue #4566 aligned the
 * devcontainer with the Flyway seeds, which give the {@code doctor} role a bare {@code o} on both, and
 * the Playwright checks that drive the module grant both objects to the login's provider number for
 * their run. These cases pin what that relies on here: the seeded denial holds, and a provider-number
 * row holding {@code x} passes beside the role's {@code o}.
 *
 * @since 2026-10-15
 */
@DisplayName("SecurityManager.hasReadAccess role rows")
@Tag("unit")
@Tag("security")
class SecurityManagerHasReadAccessUnitTest extends CarlosUnitTestBase {

    /** The session role list of the seeded test login: userrole + "," + provider number. */
    private static final String SEEDED_LOGIN = "doctor,admin,999998";
    private static final List<String> SEEDED_ROLES = List.of("doctor", "admin", "999998");

    private SecobjprivilegeDao secobjprivilegeDao;

    @BeforeEach
    void setUp() {
        secobjprivilegeDao = mock(SecobjprivilegeDao.class);
        registerMock(SecobjprivilegeDao.class, secobjprivilegeDao);
    }

    private void rows(Secobjprivilege... privileges) {
        when(secobjprivilegeDao.getByObjectNameAndRoles("_episode", SEEDED_ROLES)).thenReturn(List.of(privileges));
    }

    private static Secobjprivilege row(String roleUserGroup, String privilege) {
        return new Secobjprivilege(roleUserGroup, "_episode", privilege, 0, "999998");
    }

    @Test
    @DisplayName("should deny read when the login's only row is the seeded doctor o")
    void shouldDenyRead_whenSeededDoctorRowIsBareO() {
        rows(row("doctor", "o"));

        assertThat(new SecurityManager().hasReadAccess("_episode", SEEDED_LOGIN)).isFalse();
        verify(secobjprivilegeDao).getByObjectNameAndRoles("_episode", SEEDED_ROLES);
    }

    @Test
    @DisplayName("should grant read when the provider number holds x beside the doctor o")
    void shouldGrantRead_whenProviderNumberRowHoldsX() {
        rows(row("doctor", "o"), row("999998", "x"));

        assertThat(new SecurityManager().hasReadAccess("_episode", SEEDED_LOGIN)).isTrue();
    }

    @Test
    @DisplayName("should grant read when the doctor role holds x, as the pre-#4566 devcontainer did")
    void shouldGrantRead_whenDoctorRoleHoldsX() {
        rows(row("doctor", "x"));

        assertThat(new SecurityManager().hasReadAccess("_episode", SEEDED_LOGIN)).isTrue();
    }

    @Test
    @DisplayName("should grant read when no row names the object for any of the login's roles")
    void shouldGrantRead_whenObjectHasNoRowsForTheLogin() {
        rows();

        assertThat(new SecurityManager().hasReadAccess("_episode", SEEDED_LOGIN)).isTrue();
    }
}
