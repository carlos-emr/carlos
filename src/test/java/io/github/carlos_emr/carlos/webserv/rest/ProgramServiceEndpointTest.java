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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * CXF local-transport endpoint tests for {@link ProgramService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ProgramService REST endpoint tests")
class ProgramServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private ProgramManager2 mockProgramManager;

    @Mock
    private AdmissionManager mockAdmissionManager;

    @Override
    protected Object getServiceBean() {
        return new ProgramService(mockProgramManager, mockAdmissionManager,
                authorizeEndpoint("r", "_pmm_management"));
    }

    @BeforeEach
    void setUpProvider() {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    @Nested
    @DisplayName("GET /program/programList")
    class GetProgramList {

        @Test
        @DisplayName("should return 200 with program list")
        void shouldReturn200_whenProgramsExist() {
            ProgramProvider pp = new ProgramProvider();
            var program = new io.github.carlos_emr.carlos.PMmodule.model.Program();
            program.setId(7);
            program.setName("Primary Care");
            pp.setProgram(program);
            when(mockProgramManager.getProgramDomain(any(LoggedInInfo.class), eq("999998")))
                .thenReturn(List.of(pp));

            Response response = request().path("/program/programList").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(1);
            assertThat(wireJson.at("/content").isArray()).isTrue();
            assertThat(wireJson.at("/content")).hasSize(1);
            assertThat(wireJson.at("/content/0/id").intValue()).isEqualTo(7);
            assertThat(wireJson.at("/content/0/name").textValue()).isEqualTo("Primary Care");
        }

        /**
         * {@code program_provider.program_id} is nullable and the association is not
         * {@code optional=false}, so a membership row can carry no program at all. One such row
         * must not take the whole list down: before the guard the conversion dereferenced null
         * and the provider got a 500 instead of the programs they do belong to.
         */
        @Test
        @DisplayName("should skip a membership row that carries no program")
        void shouldReturnTheRemainingPrograms_whenARowHasNoProgram() {
            ProgramProvider orphaned = new ProgramProvider();
            ProgramProvider joined = new ProgramProvider();
            var program = new io.github.carlos_emr.carlos.PMmodule.model.Program();
            program.setId(7);
            program.setName("Primary Care");
            joined.setProgram(program);
            when(mockProgramManager.getProgramDomain(any(LoggedInInfo.class), eq("999998")))
                .thenReturn(List.of(orphaned, joined));

            Response response = request().path("/program/programList").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(1);
            assertThat(wireJson.at("/content")).hasSize(1);
            assertThat(wireJson.at("/content/0/id").intValue()).isEqualTo(7);
        }

        @Test
        @DisplayName("should return 200 with empty list when no programs")
        void shouldReturn200WithEmptyList_whenNoProgramsExist() {
            when(mockProgramManager.getProgramDomain(any(LoggedInInfo.class), eq("999998")))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/program/programList").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(0);
            assertThat(wireJson.at("/content").isArray()).isTrue();
            assertThat(wireJson.at("/content")).hasSize(0);
        }
    }
}
