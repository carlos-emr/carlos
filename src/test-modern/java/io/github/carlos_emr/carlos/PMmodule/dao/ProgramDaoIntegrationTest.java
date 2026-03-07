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
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link ProgramDao}.
 * Tests the core query methods with meaningful data setup and assertions.
 *
 * @since 2026-03-07
 */
@DisplayName("ProgramDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProgramDao dao;

    @PersistenceContext(unitName = "testPersistenceUnit")
    private EntityManager entityManager;

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(90000);

    /**
     * Creates a Program with a unique ID and required fields populated.
     */
    private Program createProgram(String name, String type, String status) {
        Program p = new Program();
        p.setId(ID_COUNTER.incrementAndGet());
        p.setName(name);
        p.setType(type);
        p.setProgramStatus(status);
        p.setMaxAllowed(100);
        p.setAddress("123 Test St");
        p.setPhone("555-0100");
        p.setFax("555-0101");
        p.setUrl("http://test.example.com");
        p.setEmail("test@example.com");
        p.setEmergencyNumber("555-0199");
        p.setHoldingTank(false);
        p.setLastUpdateDate(new Date());
        return p;
    }

    private Program saveAndFlush(Program p) {
        entityManager.persist(p);
        entityManager.flush();
        return p;
    }

    @Nested
    @DisplayName("Save and Retrieve Tests")
    class SaveAndRetrieveTests {

        @Test
        @Tag("create")
        @DisplayName("should persist program and retrieve by ID")
        void shouldPersistAndRetrieve_whenValidDataProvided() {
            Program program = createProgram("Test Program", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            saveAndFlush(program);

            Program found = dao.getProgram(program.getId());
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(program.getId());
            assertThat(found.getName()).isEqualTo("Test Program");
            assertThat(found.getType()).isEqualTo(Program.SERVICE_TYPE);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when program ID is null")
        void shouldReturnNull_whenProgramIdIsNull() {
            Program found = dao.getProgram(null);
            assertThat(found).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when program ID is zero or negative")
        void shouldReturnNull_whenProgramIdIsInvalid() {
            assertThat(dao.getProgram(0)).isNull();
            assertThat(dao.getProgram(-1)).isNull();
        }

        @Test
        @Tag("update")
        @DisplayName("should update program via saveProgram")
        void shouldUpdateProgram_whenSaved() {
            Program program = createProgram("Original Name", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            saveAndFlush(program);

            program.setName("Updated Name");
            dao.saveProgram(program);
            entityManager.flush();

            Program found = dao.getProgram(program.getId());
            assertThat(found.getName()).isEqualTo("Updated Name");
        }

        @Test
        @Tag("delete")
        @DisplayName("should remove program by ID")
        void shouldRemoveProgram_whenValidIdProvided() {
            Program program = createProgram("To Delete", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            saveAndFlush(program);

            assertThat(dao.getProgram(program.getId())).isNotNull();

            dao.removeProgram(program.getId());
            entityManager.flush();

            assertThat(dao.getProgram(program.getId())).isNull();
        }

        @Test
        @Tag("create")
        @DisplayName("should throw exception when saving null program")
        void shouldThrowException_whenSavingNullProgram() {
            assertThatThrownBy(() -> dao.saveProgram(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw exception when removing with null ID")
        void shouldThrowException_whenRemovingWithNullId() {
            assertThatThrownBy(() -> dao.removeProgram(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Query by Status and Type Tests")
    class QueryByStatusAndTypeTests {

        private Program activeService;
        private Program activeExternal;
        private Program inactiveService;
        private Program activeCommunity;

        @BeforeEach
        void setUp() {
            activeService = saveAndFlush(createProgram("Active Service", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            activeExternal = saveAndFlush(createProgram("Active External", Program.EXTERNAL_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            inactiveService = saveAndFlush(createProgram("Inactive Service", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_INACTIVE));
            activeCommunity = saveAndFlush(createProgram("Active Community", Program.COMMUNITY_TYPE, Program.PROGRAM_STATUS_ACTIVE));
        }

        @Test
        @Tag("read")
        @DisplayName("should return all programs via findAll")
        void shouldReturnAllPrograms_whenFindAllCalled() {
            List<Program> result = dao.findAll();
            assertThat(result).hasSize(4);
            assertThat(result).extracting(Program::getId)
                .containsExactlyInAnyOrder(activeService.getId(), activeExternal.getId(),
                    inactiveService.getId(), activeCommunity.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return only active programs via getAllActivePrograms")
        void shouldReturnActivePrograms_whenGetAllActiveProgramsCalled() {
            List<Program> result = dao.getAllActivePrograms();
            assertThat(result).hasSize(3);
            assertThat(result).extracting(Program::getId)
                .containsExactlyInAnyOrder(activeService.getId(), activeExternal.getId(), activeCommunity.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return active non-community programs via getActivePrograms")
        void shouldReturnActiveNonCommunityPrograms_whenGetActiveProgramsCalled() {
            List<Program> result = dao.getActivePrograms();
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Program::getId)
                .containsExactlyInAnyOrder(activeService.getId(), activeExternal.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return non-community programs via getAllPrograms")
        void shouldReturnNonCommunityPrograms_whenGetAllProgramsCalled() {
            List<Program> result = dao.getAllPrograms();
            assertThat(result).hasSize(3);
            assertThat(result).extracting(Program::getId)
                .containsExactlyInAnyOrder(activeService.getId(), activeExternal.getId(), inactiveService.getId());
        }
    }

    @Nested
    @DisplayName("Type Check Tests")
    class TypeCheckTests {

        @Test
        @Tag("read")
        @DisplayName("should identify service program correctly")
        void shouldReturnTrue_forServiceProgram() {
            Program p = saveAndFlush(createProgram("Service Prog", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            assertThat(dao.isServiceProgram(p.getId())).isTrue();
            assertThat(dao.isCommunityProgram(p.getId())).isFalse();
            assertThat(dao.isExternalProgram(p.getId())).isFalse();
        }

        @Test
        @Tag("read")
        @DisplayName("should identify community program correctly")
        void shouldReturnTrue_forCommunityProgram() {
            Program p = saveAndFlush(createProgram("Community Prog", Program.COMMUNITY_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            assertThat(dao.isCommunityProgram(p.getId())).isTrue();
            assertThat(dao.isServiceProgram(p.getId())).isFalse();
        }

        @Test
        @Tag("read")
        @DisplayName("should identify external program correctly")
        void shouldReturnTrue_forExternalProgram() {
            Program p = saveAndFlush(createProgram("External Prog", Program.EXTERNAL_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            assertThat(dao.isExternalProgram(p.getId())).isTrue();
            assertThat(dao.isServiceProgram(p.getId())).isFalse();
        }

        @Test
        @Tag("read")
        @DisplayName("should return false for non-existent program type checks")
        void shouldReturnFalse_whenProgramDoesNotExist() {
            assertThat(dao.isServiceProgram(99999)).isFalse();
            assertThat(dao.isCommunityProgram(99999)).isFalse();
            assertThat(dao.isExternalProgram(99999)).isFalse();
        }
    }

    @Nested
    @DisplayName("Name Lookup Tests")
    class NameLookupTests {

        @Test
        @Tag("read")
        @DisplayName("should return program name by ID")
        void shouldReturnProgramName_whenValidIdProvided() {
            Program p = saveAndFlush(createProgram("Lookup Name Test", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            String name = dao.getProgramName(p.getId());
            assertThat(name).isEqualTo("Lookup Name Test");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null name for non-existent program")
        void shouldReturnNull_whenProgramNotFound() {
            assertThat(dao.getProgramName(99999)).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return program ID by name")
        void shouldReturnProgramId_whenValidNameProvided() {
            Program p = saveAndFlush(createProgram("Unique Name 12345", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            Integer foundId = dao.getProgramIdByProgramName("Unique Name 12345");
            assertThat(foundId).isEqualTo(p.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return null ID for non-existent program name")
        void shouldReturnNull_whenNameNotFound() {
            assertThat(dao.getProgramIdByProgramName("Nonexistent Program XYZ")).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return null ID when name is null")
        void shouldReturnNull_whenNameIsNull() {
            assertThat(dao.getProgramIdByProgramName(null)).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return program by name via getProgramByName")
        void shouldReturnProgram_whenSearchedByName() {
            Program p = saveAndFlush(createProgram("GetByName Test", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            Program found = dao.getProgramByName("GetByName Test");
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(p.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return null for non-existent name via getProgramByName")
        void shouldReturnNull_whenGetByNameFindsNothing() {
            assertThat(dao.getProgramByName("No Such Program")).isNull();
        }
    }

    @Nested
    @DisplayName("Facility Query Tests")
    class FacilityQueryTests {

        @Test
        @Tag("read")
        @DisplayName("should return programs by facility ID")
        void shouldReturnPrograms_byFacilityId() {
            Program p1 = createProgram("Facility A Prog1", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p1.setFacilityId(501);
            saveAndFlush(p1);

            Program p2 = createProgram("Facility A Prog2", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p2.setFacilityId(501);
            saveAndFlush(p2);

            Program p3 = createProgram("Facility B Prog", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p3.setFacilityId(502);
            saveAndFlush(p3);

            List<Program> result = dao.getProgramsByFacilityId(501);
            assertThat(result).extracting(Program::getId)
                .contains(p1.getId(), p2.getId())
                .doesNotContain(p3.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return programs by facility ID and functional centre ID")
        void shouldReturnPrograms_byFacilityIdAndFunctionalCentreId() {
            Program p1 = createProgram("FC Match", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p1.setFacilityId(601);
            p1.setFunctionalCentreId("FC001");
            saveAndFlush(p1);

            Program p2 = createProgram("FC No Match", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p2.setFacilityId(601);
            p2.setFunctionalCentreId("FC002");
            saveAndFlush(p2);

            List<Program> result = dao.getProgramsByFacilityIdAndFunctionalCentreId(601, "FC001");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(p1.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return record IDs by facility ID")
        void shouldReturnRecordIds_byFacilityId() {
            Program p1 = createProgram("Record F1", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p1.setFacilityId(701);
            saveAndFlush(p1);

            Program p2 = createProgram("Record F2", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p2.setFacilityId(702);
            saveAndFlush(p2);

            List<Integer> result = dao.getRecordsByFacilityId(701);
            assertThat(result).containsExactly(p1.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should check if two programs are in same facility")
        void shouldReturnTrue_whenProgramsInSameFacility() {
            Program p1 = createProgram("Same Fac 1", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p1.setFacilityId(801);
            saveAndFlush(p1);

            Program p2 = createProgram("Same Fac 2", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p2.setFacilityId(801);
            saveAndFlush(p2);

            Program p3 = createProgram("Diff Fac", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p3.setFacilityId(802);
            saveAndFlush(p3);

            assertThat(dao.isInSameFacility(p1.getId(), p2.getId())).isTrue();
            assertThat(dao.isInSameFacility(p1.getId(), p3.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("Program Exists and Special Query Tests")
    class ProgramExistsTests {

        @Test
        @Tag("read")
        @DisplayName("should return true when program exists")
        void shouldReturnTrue_whenProgramExists() {
            Program p = saveAndFlush(createProgram("Exists Test", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            assertThat(dao.programExists(p.getId())).isTrue();
        }

        @Test
        @Tag("read")
        @DisplayName("should return false when program does not exist")
        void shouldReturnFalse_whenProgramDoesNotExist() {
            assertThat(dao.programExists(99999)).isFalse();
        }

        @Test
        @Tag("read")
        @DisplayName("should return program by site-specific field")
        void shouldReturnProgram_bySiteSpecificField() {
            Program p = createProgram("SSF Test", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p.setSiteSpecificField("unique-ssf-value-xyz");
            saveAndFlush(p);

            Program found = dao.getProgramBySiteSpecificField("unique-ssf-value-xyz");
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(p.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return null for non-existent site-specific field")
        void shouldReturnNull_whenSiteSpecificFieldNotFound() {
            assertThat(dao.getProgramBySiteSpecificField("no-such-value")).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return program for appointment view when exclusive view is appointment")
        void shouldReturnProgram_forApptView() {
            Program p = createProgram("Appt View Test", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p.setExclusiveView("appointment");
            saveAndFlush(p);

            Program found = dao.getProgramForApptView(p.getId());
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(p.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return null for appointment view when exclusive view is not appointment")
        void shouldReturnNull_whenExclusiveViewIsNotAppointment() {
            Program p = createProgram("No Appt View", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p.setExclusiveView("no");
            saveAndFlush(p);

            assertThat(dao.getProgramForApptView(p.getId())).isNull();
        }
    }

    @Nested
    @DisplayName("Holding Tank Tests")
    class HoldingTankTests {

        @Test
        @Tag("read")
        @DisplayName("should return holding tank program")
        void shouldReturnHoldingTankProgram_whenOneExists() {
            Program p = createProgram("HT Program", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p.setHoldingTank(true);
            saveAndFlush(p);

            Program found = dao.getHoldingTankProgram();
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(p.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when no holding tank program exists")
        void shouldReturnNull_whenNoHoldingTankProgram() {
            saveAndFlush(createProgram("Not HT", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE));
            assertThat(dao.getHoldingTankProgram()).isNull();
        }
    }

    @Nested
    @DisplayName("Gender Type Query Tests")
    class GenderTypeTests {

        @Test
        @Tag("read")
        @DisplayName("should return programs by gender type")
        void shouldReturnPrograms_byGenderType() {
            Program pMen = createProgram("Men Program", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            pMen.setManOrWoman("M");
            saveAndFlush(pMen);

            Program pWomen = createProgram("Women Program", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            pWomen.setManOrWoman("F");
            saveAndFlush(pWomen);

            List<Program> menResults = dao.getProgramByGenderType("M");
            assertThat(menResults).hasSize(1);
            assertThat(menResults.get(0).getId()).isEqualTo(pMen.getId());

            List<Program> womenResults = dao.getProgramByGenderType("F");
            assertThat(womenResults).hasSize(1);
            assertThat(womenResults.get(0).getId()).isEqualTo(pWomen.getId());
        }
    }

    @Nested
    @DisplayName("Programs by Type Tests")
    class ProgramsByTypeTests {

        @Test
        @Tag("read")
        @DisplayName("should return programs by type with facility and active filters")
        void shouldReturnPrograms_byTypeWithFilters() {
            Program p1 = createProgram("Service Active F1", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p1.setFacilityId(901);
            saveAndFlush(p1);

            Program p2 = createProgram("Service Inactive F1", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_INACTIVE);
            p2.setFacilityId(901);
            saveAndFlush(p2);

            Program p3 = createProgram("External Active F1", Program.EXTERNAL_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p3.setFacilityId(901);
            saveAndFlush(p3);

            // Filter by type=Service, facility=901, active=true
            List<Program> result = dao.getProgramsByType(901, Program.SERVICE_TYPE, true);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(p1.getId());

            // Filter by type=Service, facility=901, active=null (both)
            List<Program> allServiceInFacility = dao.getProgramsByType(901, Program.SERVICE_TYPE, null);
            assertThat(allServiceInFacility).hasSize(2);
            assertThat(allServiceInFacility).extracting(Program::getId)
                .containsExactlyInAnyOrder(p1.getId(), p2.getId());
        }
    }

    @Nested
    @DisplayName("Records Updated Since Time Tests")
    class RecordsUpdatedSinceTimeTests {

        @Test
        @Tag("read")
        @DisplayName("should return program IDs added or updated since date for facility")
        void shouldReturnUpdatedProgramIds_sinceDate() {
            Date oldDate = new Date(System.currentTimeMillis() - 86400000L); // 1 day ago
            Date recentDate = new Date(System.currentTimeMillis() + 86400000L); // 1 day in future

            Program p1 = createProgram("Updated Recent", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p1.setFacilityId(1001);
            p1.setLastUpdateDate(recentDate);
            saveAndFlush(p1);

            Program p2 = createProgram("Updated Old", Program.SERVICE_TYPE, Program.PROGRAM_STATUS_ACTIVE);
            p2.setFacilityId(1001);
            p2.setLastUpdateDate(oldDate);
            saveAndFlush(p2);

            Date cutoff = new Date(); // now
            List<Integer> result = dao.getRecordsAddedAndUpdatedSinceTime(1001, cutoff);
            assertThat(result).containsExactly(p1.getId());
        }
    }
}
