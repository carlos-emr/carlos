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
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.PMmodule.dao.DefaultRoleAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.utility.ProgramAccessCache;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.CaisiAccessTypeDao;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.CarlosProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Draft and history authorization must reject incomplete legacy roles without failing the request.
 * @since 2026-10-24
 */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementNoteRoleFilterUnitTest extends CarlosUnitTestBase {
    private CaseManagementManagerImpl manager;
    private ProgramProvider membership;
    private RoleProgramAccessDAO defaults;
    private Map<String, ProgramAccess> permissions;
    private MockedStatic<RoleCache> roles;
    private MockedStatic<ProgramAccessCache> access;
    private MockedStatic<CarlosProperties> properties;

    @BeforeEach
    void setUp() {
        // Cache class initialization resolves these beans before Mockito installs its static mock.
        createAndRegisterMock(SecroleDao.class);
        createAndRegisterMock(DefaultRoleAccessDAO.class);
        createAndRegisterMock(CaisiAccessTypeDao.class);
        createAndRegisterMock(ProgramAccessDAO.class);
        roles = mockStatic(RoleCache.class);
        access = mockStatic(ProgramAccessCache.class);
        properties = mockStatic(CarlosProperties.class);
        var settings = mock(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(settings);
        when(settings.getBooleanProperty("FILTER_ON_FACILITY", "true")).thenReturn(false);

        manager = new CaseManagementManagerImpl();
        var providers = mock(ProgramProviderDAO.class);
        manager.setProgramProviderDao(providers);
        defaults = mock(RoleProgramAccessDAO.class);
        manager.setRoleProgramAccessDAO(defaults);
        membership = new ProgramProvider();
        membership.setRole(role(7L, "Doctor"));
        when(providers.getProgramProviderByProviderProgramId("999998", 100L)).thenReturn(List.of(membership));
        roles.when(() -> RoleCache.getRole(7L)).thenReturn(membership.getRole());
        roles.when(() -> RoleCache.getRole(8L)).thenReturn(role(8L, "Nurse"));
        permissions = new HashMap<>();
        access.when(() -> ProgramAccessCache.getAccessMap(100L)).thenReturn(permissions);
    }

    @AfterEach
    void closeCaches() {
        if (properties != null) properties.close();
        if (access != null) access.close();
        if (roles != null) roles.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-role", "9223372036854775808", "999"})
    void shouldDenyLegacyNoteWithoutThrowing_whenReporterRoleCannotBeResolved(String reporterRole) {
        assertThat(manager.filterNotes(null, "999998", List.of(note(reporterRole)), "100")).isEmpty();
        verifyNoInteractions(defaults);
    }

    @Test
    void shouldDenyNoteWithoutThrowing_whenTheCachedRoleHasNoName() {
        roles.when(() -> RoleCache.getRole(8L)).thenReturn(role(8L, null));
        assertThat(manager.filterNotes(null, "999998", List.of(note("8")), "100")).isEmpty();
        verifyNoInteractions(defaults);
    }

    @Test
    void shouldDenyNotesWithoutThrowing_whenTheProgramMembershipHasNoRole() {
        membership.setRole(null);
        assertThat(manager.filterNotes(null, "999998", List.of(note("7")), "100")).isEmpty();
        verifyNoInteractions(defaults);
        access.verifyNoInteractions();
    }

    @Test
    void shouldRetainSameRoleAccess_whenTheReporterRoleIsValid() {
        var note = note("7");
        assertThat(manager.filterNotes(null, "999998", List.of(note), "100")).containsExactly(note);
    }

    @Test
    void shouldRetainExplicitCrossRoleAccess_whenTheProgramAllowsAllRoles() {
        var note = note("8");
        var permission = new ProgramAccess();
        permission.setAllRoles(true);
        permissions.put("read nurse notes", permission);
        assertThat(manager.filterNotes(null, "999998", List.of(note), "100")).containsExactly(note);
    }

    @Test
    void shouldRetainDefaultCrossRoleAccess_whenTheDefaultGrantExists() {
        var note = note("8");
        when(defaults.hasAccess("read nurse notes", 7L)).thenReturn(true);
        assertThat(manager.filterNotes(null, "999998", List.of(note), "100")).containsExactly(note);
    }

    @Test
    void shouldDenyCrossRoleAccess_whenNeitherTheProgramNorDefaultGrantsIt() {
        assertThat(manager.filterNotes(null, "999998", List.of(note("8")), "100")).isEmpty();
    }

    private static Secrole role(long id, String name) {
        var role = new Secrole();
        role.setId(id);
        role.setName(name);
        return role;
    }

    private static CaseManagementNote note(String reporterRole) {
        var note = new CaseManagementNote();
        note.setId(501L);
        note.setReporter_caisi_role(reporterRole);
        return note;
    }
}
