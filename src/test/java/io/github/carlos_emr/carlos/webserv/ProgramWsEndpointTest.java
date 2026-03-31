/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProgramProviderTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProgramTransfer;

/**
 * SOAP endpoint tests for {@link ProgramWs} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("ProgramWs SOAP endpoint tests")
class ProgramWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private ProgramManager2 mockProgramManager;

    @Override
    protected Object getServiceBean() {
        ProgramWs ws = new ProgramWs();
        injectDependency(ws, "programManager", mockProgramManager);
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return ProgramWs.class;
    }

    @Test
    @DisplayName("should return all programs via SOAP")
    void shouldReturnAllPrograms_viaSoap() {
        Program program = new Program();
        program.setId(1L);
        program.setName("Test Program");
        when(mockProgramManager.getAllPrograms(any(LoggedInInfo.class)))
            .thenReturn(List.of(program));

        ProgramWs proxy = createClient(ProgramWs.class);
        ProgramTransfer[] results = proxy.getAllPrograms();

        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("should return empty array when no programs")
    void shouldReturnEmptyArray_whenNoProgramsExist() {
        when(mockProgramManager.getAllPrograms(any(LoggedInInfo.class)))
            .thenReturn(Collections.emptyList());

        ProgramWs proxy = createClient(ProgramWs.class);
        ProgramTransfer[] results = proxy.getAllPrograms();

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return all program providers via SOAP")
    void shouldReturnAllProgramProviders_viaSoap() {
        ProgramProvider pp = new ProgramProvider();
        when(mockProgramManager.getAllProgramProviders(any(LoggedInInfo.class)))
            .thenReturn(List.of(pp));

        ProgramWs proxy = createClient(ProgramWs.class);
        ProgramProviderTransfer[] results = proxy.getAllProgramProviders();

        assertThat(results).isNotEmpty();
    }
}
