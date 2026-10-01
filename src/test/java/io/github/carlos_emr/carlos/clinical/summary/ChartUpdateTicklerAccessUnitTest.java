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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChartUpdateTicklerAccessUnitTest {
    @Test void shouldPreserveCreatorAccess_whenAssigneeHasNoProgramRole() {
        var manager = new TicklerManagerImpl();
        var memberships = mock(ProgramProviderDAO.class);
        var access = mock(ProgramAccessDAO.class);
        var notes = mock(CaseManagementManager.class);
        ReflectionTestUtils.setField(manager, "programProviderDAO", memberships);
        ReflectionTestUtils.setField(manager, "programAccessDAO", access);
        ReflectionTestUtils.setField(manager, "caseManagementManager", notes);
        var role = new Secrole();
        role.setRoleName("doctor");
        var member = new ProgramProvider();
        member.setRole(role);
        when(memberships.getProgramProviderByProviderProgramId("101", 10016L)).thenReturn(List.of(member));
        when(memberships.getProgramProviderByProviderProgramId("102", 10016L)).thenReturn(List.of());
        when(notes.convertProgramAccessListToMap(anyList())).thenReturn(Map.of());
        var tickler = new Tickler();
        tickler.setId(7);
        tickler.setProgramId(10016);
        tickler.setTaskAssignedTo("102");
        tickler.setCreator("101");
        assertThat(manager.filterTicklersByAccess(List.of(tickler), "101", "10016")).containsExactly(tickler);
        tickler.setCreator("103");
        assertThat(manager.filterTicklersByAccess(List.of(tickler), "101", "10016")).isEmpty();
        var unconfiguredAssignee = new ProgramProvider();
        when(memberships.getProgramProviderByProviderProgramId("102", 10016L)).thenReturn(List.of(unconfiguredAssignee));
        assertThat(manager.filterTicklersByAccess(List.of(tickler), "101", "10016")).isEmpty();
        member.setRole(null);
        tickler.setCreator("101");
        assertThat(manager.filterTicklersByAccess(List.of(tickler), "101", "10016")).containsExactly(tickler);
        tickler.setCreator("103");
        assertThat(manager.filterTicklersByAccess(List.of(tickler), "101", "10016")).isEmpty();
        member.setRole(new Secrole());
        tickler.setCreator("101");
        assertThat(manager.filterTicklersByAccess(List.of(tickler), "101", "10016")).containsExactly(tickler);
    }
}
