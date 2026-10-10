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
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.TicklerManagerImpl;
import io.github.carlos_emr.carlos.model.security.Secrole;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChartUpdateTicklerAccessUnitTest {
    private final ProgramProviderDAO memberships = mock(ProgramProviderDAO.class);
    private final CaseManagementManager notes = mock(CaseManagementManager.class);
    private final TicklerManagerImpl manager = new TicklerManagerImpl();

    ChartUpdateTicklerAccessUnitTest() {
        ReflectionTestUtils.setField(manager, "programProviderDAO", memberships);
        ReflectionTestUtils.setField(manager, "programAccessDAO", mock(ProgramAccessDAO.class));
        ReflectionTestUtils.setField(manager, "caseManagementManager", notes);
        when(notes.convertProgramAccessListToMap(anyList())).thenReturn(Map.of());
    }

    // Viewer 101 opens a tickler assigned to 102; without a usable role match only the creator keeps access.
    @ParameterizedTest(name = "viewer role {0}, assignee membership {1}, creator {2}: visible={3}")
    @CsvSource({
        "doctor,  none,     101, true",
        "doctor,  none,     103, false",
        "doctor,  roleless, 103, false",
        "missing, roleless, 101, true",
        "missing, roleless, 103, false",
        "unnamed, roleless, 101, true"
    })
    void shouldPreserveCreatorAccess_whenAssigneeOrViewerHasNoProgramRole(String viewerRole, String assigneeMembership,
            String creator, boolean visible) {
        var member = new ProgramProvider();
        member.setRole(switch (viewerRole) {
            case "doctor" -> {
                var role = new Secrole();
                role.setRoleName("doctor");
                yield role;
            }
            case "unnamed" -> new Secrole();
            default -> null;
        });
        when(memberships.getProgramProviderByProviderProgramId("101", 10016L)).thenReturn(List.of(member));
        when(memberships.getProgramProviderByProviderProgramId("102", 10016L))
                .thenReturn("roleless".equals(assigneeMembership) ? List.of(new ProgramProvider()) : List.of());
        var tickler = new Tickler();
        tickler.setId(7);
        tickler.setProgramId(10016);
        tickler.setTaskAssignedTo("102");
        tickler.setCreator(creator);
        assertThat(manager.filterTicklersByAccess(List.of(tickler), "101", "10016"))
                .isEqualTo(visible ? List.of(tickler) : List.of());
    }
}
