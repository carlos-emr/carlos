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
package io.github.carlos_emr.carlos.pmmodule.dao;

import io.github.carlos_emr.carlos.pmmodule.model.Program;
import io.github.carlos_emr.carlos.pmmodule.model.ProgramTeam;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProgramTeamDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramTeamDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private ProgramTeamDAO programTeamDAO;

    private Program createProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setType("community");
        program.setProgramStatus("active");
        hibernateTemplate.save(program);
        return program;
    }

    private ProgramTeam createTeam(Integer programId, String teamName) {
        ProgramTeam team = new ProgramTeam();
        team.setProgramId(programId);
        team.setName(teamName);
        hibernateTemplate.save(team);
        return team;
    }

    @Nested
    @DisplayName("teamNameExists (programId, teamName)")
    class TeamNameExists {

        @Test
        @Tag("query")
        @DisplayName("should return true only when both program and name match")
        void shouldReturnTrueOnlyWhenBothMatch() {
            Program p1 = createProgram("PT-Program-1");
            Program p2 = createProgram("PT-Program-2");
            createTeam(p1.getId(), "Team For P1");
            createTeam(p2.getId(), "Team For P2");
            hibernateTemplate.flush();

            // Assert team exists in the correct program
            assertThat(programTeamDAO.teamNameExists(p1.getId(), "Team For P1")).isTrue();
            assertThat(programTeamDAO.teamNameExists(p2.getId(), "Team For P2")).isTrue();

            // Assert team does not exist in the wrong program
            assertThat(programTeamDAO.teamNameExists(p1.getId(), "Team For P2")).isFalse();
            assertThat(programTeamDAO.teamNameExists(p2.getId(), "Team For P1")).isFalse();

            // Assert non-existent team is false
            assertThat(programTeamDAO.teamNameExists(p1.getId(), "Missing Team")).isFalse();
        }
    }

    @Nested
    @DisplayName("getProgramTeams (programId)")
    class GetProgramTeams {

        @Test
        @Tag("query")
        @DisplayName("should filter teams by requested program only")
        void shouldFilterByProgramOnly() {
            Program p1 = createProgram("PT-Program-3");
            Program p2 = createProgram("PT-Program-4");
            ProgramTeam t1 = createTeam(p1.getId(), "One");
            createTeam(p1.getId(), "Two");
            ProgramTeam otherProgram = createTeam(p2.getId(), "Other");
            hibernateTemplate.flush();

            List<ProgramTeam> results = programTeamDAO.getProgramTeams(p1.getId());

            assertThat(results)
                .extracting(ProgramTeam::getProgramId)
                .containsOnly(p1.getId());
            assertThat(results)
                .extracting(ProgramTeam::getId)
                .contains(t1.getId())
                .doesNotContain(otherProgram.getId());
        }
    }

    @Nested
    @DisplayName("validation guardrails")
    class ValidationGuardrails {

        @Test
        @Tag("read")
        @DisplayName("should throw for invalid teamNameExists inputs")
        void shouldThrowForInvalidTeamNameExistsInputs() {
            assertThatThrownBy(() -> programTeamDAO.teamNameExists(null, "Team"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programTeamDAO.teamNameExists(0, "Team"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programTeamDAO.teamNameExists(1, ""))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("read")
        @DisplayName("should throw for invalid getProgramTeams inputs")
        void shouldThrowForInvalidGetProgramTeamsInputs() {
            assertThatThrownBy(() -> programTeamDAO.getProgramTeams(null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programTeamDAO.getProgramTeams(0))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @Tag("create")
    @Tag("read")
    @DisplayName("should persist and retrieve team via save/get roundtrip")
    void shouldPersistAndRetrieveTeamRoundtrip() {
        Program program = createProgram("PT-Program-5");

        ProgramTeam newTeam = new ProgramTeam();
        newTeam.setProgramId(program.getId());
        newTeam.setName("Roundtrip Team");

        programTeamDAO.saveProgramTeam(newTeam);
        hibernateTemplate.flush();

        ProgramTeam loaded = programTeamDAO.getProgramTeam(newTeam.getId());
        List<ProgramTeam> byProgram = programTeamDAO.getProgramTeams(program.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("Roundtrip Team");
        assertThat(byProgram).extracting(ProgramTeam::getId).contains(newTeam.getId());
    }
}
