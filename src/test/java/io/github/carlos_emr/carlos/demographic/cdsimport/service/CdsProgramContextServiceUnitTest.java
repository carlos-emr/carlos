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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import io.github.carlos_emr.carlos.commn.dao.CdsImportProgramDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProgramContextDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProgramMembershipDto;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CdsProgramContextServiceUnitTest {
    private CdsImportProgramDao dao;
    private CdsProgramContextService service;
    @BeforeEach void prepare() { dao=mock(CdsImportProgramDao.class); service=new CdsProgramContextService(dao); }

    @Test void shouldPreferActualDefault_evenWhenItsMembershipWouldBeBeyondTheFirstPage() {
        when(dao.preferred("77")).thenReturn(5000);
        when(dao.metadata(5000)).thenReturn(new CdsProgramContextDto(5000,"Preferred","active",2));
        when(dao.member("77",5000)).thenReturn(true);
        assertThat(service.defaultProgramId("77")).isEqualTo(5000);
        verify(dao,never()).memberships(anyString(),anyLong());
    }

    @Test void shouldFallBackToOldestLiveActiveMembership_whenDefaultWasRemovedOrMembershipRevoked() {
        when(dao.preferred("77")).thenReturn(5000);
        when(dao.memberships("77",0)).thenReturn(List.of(new CdsProgramMembershipDto(12,34,"inactive"),
                new CdsProgramMembershipDto(18,56,"active")));
        assertThat(service.defaultProgramId("77")).isEqualTo(56);
        when(dao.metadata(5000)).thenReturn(new CdsProgramContextDto(5000,"Preferred","active",1));
        when(dao.member("77",5000)).thenReturn(false);
        assertThat(service.defaultProgramId("77")).isEqualTo(56);
    }

    @Test void shouldPreserveExactActiveStatus_andPageWithoutAccumulatingAllMemberships() {
        when(dao.preferred("77")).thenReturn(5000);
        when(dao.metadata(5000)).thenReturn(new CdsProgramContextDto(5000,"Preferred","ACTIVE",1));
        when(dao.memberships("77",0)).thenReturn(IntStream.rangeClosed(1,100)
                .mapToObj(n->new CdsProgramMembershipDto(n,n,"inactive")).toList());
        when(dao.memberships("77",100)).thenReturn(List.of(new CdsProgramMembershipDto(101,7000,"ACTIVE"),
                new CdsProgramMembershipDto(102,8000,"active")));
        assertThat(service.defaultProgramId("77")).isEqualTo(8000);
        verify(dao).memberships("77",100);
        verify(dao,never()).member("77",5000);
    }

    @Test void shouldReturnNoDefault_whenProviderHasNoActiveMembership() {
        when(dao.memberships("77",0)).thenReturn(List.of());
        assertThat(service.defaultProgramId("77")).isNull();
    }

    @Test void shouldPropagateStorageFailure_insteadOfSilentlySelectingAnotherProgram() {
        when(dao.preferred("77")).thenThrow(new IllegalStateException("synthetic database unavailable"));
        assertThatThrownBy(()->service.defaultProgramId("77")).isInstanceOf(IllegalStateException.class);
        verify(dao,never()).memberships(anyString(),anyLong());
    }

    @Test void shouldRejectNonAdvancingPages_withoutLoopingOrGuessing() {
        when(dao.memberships("77",0)).thenReturn(List.of(new CdsProgramMembershipDto(0,34,"active")));
        assertThatThrownBy(()->service.defaultProgramId("77")).hasMessage("PROGRAM_CONTEXT_PAGE_INVALID");
    }
}
