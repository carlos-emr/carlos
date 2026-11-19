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
package io.github.carlos_emr.carlos.lab.service;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7LinkDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7PidDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Link;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.util.List;

@Tag("unit")
@Tag("lab")
class PathnetPatientLinkServiceUnitTest extends CarlosUnitTestBase {
    private Hl7PidDao pids;
    private Hl7LinkDao links;
    private MrpRoutingService routing;
    private Hl7Pid pid;
    private PathnetPatientLinkService service;

    @BeforeEach
    void prepareService() {
        pids = mock(Hl7PidDao.class);
        links = mock(Hl7LinkDao.class);
        routing = mock(MrpRoutingService.class);
        pid = new Hl7Pid();
        pid.setId(3);
        pid.setMessageId(7);
        when(pids.find(3)).thenReturn(pid);
        service = new PathnetPatientLinkService(pids, links, routing);
    }

    @Test
    void shouldApplySelectionsInOneBatch_whenExplicitAndConfirmedPatientsAreSubmitted() {
        Hl7Link link = new Hl7Link();
        link.setDemographicNo(42);
        when(links.find(3)).thenReturn(link);
        service.linkAll(List.of(new PathnetPatientLinkService.Selection(3, 42),
                new PathnetPatientLinkService.Selection(3, null)), "actor");
        verify(routing, times(2)).matchPatientLab("7", "BCP", 42, "actor");
    }

    @Test
    void shouldMatchMessageRatherThanPid_whenPatientExplicitlySelected() {
        service.link(3, 42, "actor");
        verify(routing).matchPatientLab("7", "BCP", 42, "actor");
        verifyNoInteractions(links);
    }

    @Test
    void shouldConfirmPersistedPatient_whenNoReplacementSelected() {
        Hl7Link link = new Hl7Link();
        link.setDemographicNo(42);
        when(links.find(3)).thenReturn(link);
        service.link(3, null, "actor");
        verify(routing).matchPatientLab("7", "BCP", 42, "actor");
    }

    @Test
    void shouldRefuseConfirmation_whenNoLegacyLinkExists() {
        assertThatThrownBy(() -> service.link(3, null, "actor")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(routing);
    }

    @Test
    void shouldRefuseConfirmation_whenPatientIsStillUnmatched() {
        when(links.find(3)).thenReturn(new Hl7Link());
        assertThatThrownBy(() -> service.link(3, null, "actor")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(routing);
    }

    @Test
    void shouldRejectUnknownLab_whenPidDoesNotExist() {
        when(pids.find(3)).thenReturn(null);
        assertThatThrownBy(() -> service.link(3, 42, "actor")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(links, routing);
    }

    @Test
    void shouldRejectDisconnectedLab_whenMessageKeyIsMissing() {
        pid.setMessageId(0);
        assertThatThrownBy(() -> service.link(3, 42, "actor")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(links, routing);
    }

    @Test
    void shouldPropagateDatabaseFailure_whenTransactionalMatchingFails() {
        doThrow(new PersistenceException("injected routing failure")).when(routing)
                .matchPatientLab("7", "BCP", 42, "actor");
        assertThatThrownBy(() -> service.link(3, 42, "actor")).isInstanceOf(PersistenceException.class);
    }
}
