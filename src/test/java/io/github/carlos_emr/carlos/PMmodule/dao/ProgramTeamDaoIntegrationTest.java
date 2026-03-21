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
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramTeam;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link ProgramTeamDAO} positional-parameter HQL queries.
 *
 * <p>Validates {@code teamNameExists} (two-parameter binding: programId + teamName)
 * and {@code getProgramTeams} (single-parameter binding: programId), plus input
 * validation guardrails and save/get roundtrip persistence.</p>
 *
 * @since 2026-02-12
 * @see ProgramTeamDAO
 */
@DisplayName("ProgramTeamDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramTeamDaoIntegrationTest extends CarlosTestBase {

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
        @DisplayName("should return true only when both program and name match, and false for wrong program, wrong name, or missing team")
        void shouldReturnTrue_whenBothProgramAndNameMatch() {
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
        void shouldFilterTeams_byRequestedProgramOnly() {
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
        void shouldThrow_forInvalidTeamNameExistsInputs() {
            assertThatThrownBy(() -> programTeamDAO.teamNameExists(null, "Team"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programTeamDAO.teamNameExists(0, "Team"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programTeamDAO.teamNameExists(1, null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programTeamDAO.teamNameExists(1, ""))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("read")
        @DisplayName("should throw for invalid getProgramTeams inputs")
        void shouldThrow_forInvalidGetProgramTeamsInputs() {
            assertThatThrownBy(() -> programTeamDAO.getProgramTeams(null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programTeamDAO.getProgramTeams(0))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("teamExists (id)")
    class TeamExists {

        @Test
        @Tag("read")
        @DisplayName("should return true when team exists")
        void shouldReturnTrue_whenTeamExists() {
            Program program = createProgram("TE-Program-1");
            ProgramTeam team = createTeam(program.getId(), "Existing Team");
            hibernateTemplate.flush();

            assertThat(programTeamDAO.teamExists(team.getId())).isTrue();
        }

        @Test
        @Tag("read")
        @DisplayName("should return false when team does not exist")
        void shouldReturnFalse_whenTeamDoesNotExist() {
            assertThat(programTeamDAO.teamExists(Integer.MAX_VALUE)).isFalse();
        }

        @Test
        @Tag("read")
        @DisplayName("should return false for null id")
        void shouldReturnFalse_forNullId() {
            assertThat(programTeamDAO.teamExists(null)).isFalse();
        }

        @Test
        @Tag("read")
        @DisplayName("should return false after team is removed from session")
        void shouldReturnFalse_afterTeamRemovedFromSession() {
            Program program = createProgram("TE-Program-2");
            ProgramTeam team = createTeam(program.getId(), "Transient Team");
            hibernateTemplate.flush();
            Integer savedId = team.getId();

            hibernateTemplate.delete(team);
            hibernateTemplate.flush();

            assertThat(programTeamDAO.teamExists(savedId)).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteProgramTeam (id)")
    class DeleteProgramTeam {

        @Test
        @Tag("delete")
        @DisplayName("should remove team so it no longer exists")
        void shouldRemoveTeam_soItNoLongerExists() {
            Program program = createProgram("DPT-Program-1");
            ProgramTeam team = createTeam(program.getId(), "To Be Removed");
            hibernateTemplate.flush();
            Integer teamId = team.getId();

            programTeamDAO.deleteProgramTeam(teamId);
            hibernateTemplate.flush();

            assertThat(programTeamDAO.teamExists(teamId)).isFalse();
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw IllegalArgumentException for null id")
        void shouldThrowIllegalArgumentException_forNullId() {
            assertThatThrownBy(() -> programTeamDAO.deleteProgramTeam(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw IllegalArgumentException for zero id")
        void shouldThrowIllegalArgumentException_forZeroId() {
            assertThatThrownBy(() -> programTeamDAO.deleteProgramTeam(0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw EmptyResultDataAccessException when team does not exist")
        void shouldThrowEmptyResultDataAccessException_whenTeamDoesNotExist() {
            assertThatThrownBy(() -> programTeamDAO.deleteProgramTeam(Integer.MAX_VALUE))
                .isInstanceOf(EmptyResultDataAccessException.class);
        }
    }

    @Test
    @Tag("create")
    @Tag("read")
    @DisplayName("should persist and retrieve team via save/get roundtrip")
    void shouldPersistAndRetrieveTeam_viaSaveGetRoundtrip() {
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
