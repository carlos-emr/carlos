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
import io.github.carlos_emr.carlos.commn.dao.AdmissionDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actual read, eager-load and write commits preserve nullable legacy program storage while
 * primitive flag APIs and explicit restriction-day writes retain their historical defaults.
 * @since 2026-10-26
 */
@Tag("integration") @Tag("dao") @Tag("pmmodule")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProgramNullableFieldsFlushIntegrationTest extends CarlosTestBase {
    private static final String RAW_COLUMNS = "holdingTank,allowBatchAdmission,allowBatchDischarge,hic,userDefined,defaultServiceRestrictionDays";
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ProgramDao programs;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;
    private final List<Fixture> fixtures = new ArrayList<>();

    private record Fixture(Integer program, Integer patient) { }
    private TransactionTemplate transaction() { return new TransactionTemplate(transactions); }

    private Program newProgram() {
        var program = new Program(); program.setName("FAKE-Nullable-Program"); program.setType(Program.SERVICE_TYPE);
        program.setProgramStatus(Program.PROGRAM_STATUS_ACTIVE); program.setMaxAllowed(10);
        program.setAgeMin(1); program.setAgeMax(200); program.setExclusiveView("no");
        return program;
    }

    private Fixture createLegacy(Integer days, boolean admission) {
        Fixture fixture = transaction().execute(status -> {
            Program program = newProgram(); entityManager.persist(program); entityManager.flush();
            Integer patientId = null;
            if (admission) {
                var patient = new Demographic(); patient.setFirstName("FAKE-Program"); patient.setLastName("FAKE-Admission");
                patient.setProviderNo("999998"); patient.setHcType("ON"); patient.setSex("F"); patient.setPrefName("");
                patient.setYearOfBirth("1980"); patient.setMonthOfBirth("01"); patient.setDateOfBirth("02");
                patient.setPatientStatus("AC"); patient.setRosterStatus("NR"); entityManager.persist(patient); entityManager.flush();
                patientId = patient.getDemographicNo();
                var row = new Admission(); row.setClientId(patientId); row.setProgramId(program.getId());
                row.setProviderNo("999998"); row.setAdmissionStatus(Admission.STATUS_CURRENT); row.setAdmissionDate(new Date());
                entityManager.persist(row); entityManager.flush();
            }
            entityManager.createNativeQuery("UPDATE program SET holdingTank=NULL,allowBatchAdmission=NULL,"
                    + "allowBatchDischarge=NULL,hic=NULL,userDefined=NULL,defaultServiceRestrictionDays=:days WHERE id=:id")
                    .setParameter("days",days).setParameter("id",program.getId()).executeUpdate();
            entityManager.clear(); return new Fixture(program.getId(),patientId);
        });
        fixtures.add(fixture); return fixture;
    }

    private List<?> storedRow(Integer id) {
        return transaction().execute(status -> entityManager.createNativeQuery("SELECT * FROM program WHERE id=:id")
                .setParameter("id",id).getResultList());
    }

    private Object[] storedValues(Integer id) {
        return transaction().execute(status -> (Object[]) entityManager.createNativeQuery(
                "SELECT "+RAW_COLUMNS+" FROM program WHERE id=:id").setParameter("id",id).getSingleResult());
    }

    private void assertFriendlyDefaults(Program program) {
        assertThat(program.isHoldingTank()).isFalse(); assertThat(program.isAllowBatchAdmission()).isFalse();
        assertThat(program.isAllowBatchDischarge()).isFalse(); assertThat(program.isHic()).isFalse();
        assertThat(program.isUserDefined()).isTrue(); assertThat(program.getDefaultServiceRestrictionDays()).isEqualTo(30);
    }

    private void setFlags(Program program, boolean value) {
        program.setHoldingTank(value); program.setAllowBatchAdmission(value); program.setAllowBatchDischarge(value);
        program.setHic(value); program.setUserDefined(value);
    }

    @AfterEach
    void removeCommittedFixtures() {
        transaction().executeWithoutResult(status -> {
            for (Fixture fixture : fixtures) {
                entityManager.createNativeQuery("DELETE FROM admission WHERE program_id=:id").setParameter("id",fixture.program()).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM program WHERE id=:id").setParameter("id",fixture.program()).executeUpdate();
                if (fixture.patient()!=null) entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no=:id")
                        .setParameter("id",fixture.patient()).executeUpdate();
            }
        });
    }

    @Test
    void shouldPreserveNullFlagStorage_whenTheirFallbacksAreReadWithValidRestrictionDays() {
        Fixture fixture = createLegacy(30,false); List<?> before = storedRow(fixture.program());
        transaction().executeWithoutResult(status -> assertFriendlyDefaults(programs.getProgram(fixture.program())));
        assertThat(storedRow(fixture.program())).usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest @NullSource @ValueSource(ints={0,-1})
    void shouldPreserveRawValues_whenInvalidRestrictionDaysAreReadAndTheTransactionCommits(Integer days) {
        Fixture fixture = createLegacy(days,false); List<?> before = storedRow(fixture.program());
        transaction().executeWithoutResult(status -> assertFriendlyDefaults(programs.getProgram(fixture.program())));
        assertThat(storedRow(fixture.program())).usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest @NullSource @ValueSource(ints={0,-1})
    void shouldPreserveRawValues_whenAdmissionEagerlyLoadsTheProgramAndTheTransactionCommits(Integer days) {
        Fixture fixture = createLegacy(days,true); List<?> before = storedRow(fixture.program());
        transaction().executeWithoutResult(status -> {
            var dao = new AdmissionDaoImpl(); ReflectionTestUtils.setField(dao,"entityManager",entityManager);
            List<Admission> rows = dao.getAdmissions(fixture.patient()); assertThat(rows).hasSize(1);
            Program program = rows.getFirst().getProgram(); assertThat(Hibernate.isInitialized(program)).isTrue();
            assertThat(program.getId()).isEqualTo(fixture.program()); assertFriendlyDefaults(program);
        });
        assertThat(storedRow(fixture.program())).usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest @NullSource @ValueSource(ints={0,-1,30})
    void shouldPreserveRawValues_whenAnUnchangedDetachedProgramIsMerged(Integer days) {
        Fixture fixture = createLegacy(days,false); List<?> before = storedRow(fixture.program());
        Program detached = transaction().execute(status -> programs.getProgram(fixture.program()));
        transaction().executeWithoutResult(status -> entityManager.merge(detached));
        assertThat(storedRow(fixture.program())).usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest @NullSource @ValueSource(ints={0,-1,30})
    void shouldPreserveRawValues_whenAnUnrelatedDescriptionIsWritten(Integer days) {
        Fixture fixture = createLegacy(days,false);
        transaction().executeWithoutResult(status -> programs.getProgram(fixture.program()).setDescription("FAKE-Changed"));
        assertThat(storedValues(fixture.program())).containsExactly(null,null,null,null,null,days);
        Object description = transaction().execute(status -> entityManager.createNativeQuery("SELECT description FROM program WHERE id=:id")
                .setParameter("id",fixture.program()).getSingleResult());
        assertThat(description).isEqualTo("FAKE-Changed");
    }

    @Test
    void shouldRetainHistoricalDefaults_whenANewProgramIsInserted() {
        Integer id = transaction().execute(status -> { Program program=newProgram(); entityManager.persist(program); entityManager.flush(); return program.getId(); });
        fixtures.add(new Fixture(id,null));
        assertThat(storedValues(id)).containsExactly(false,false,false,false,true,30);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldKeepPrimitiveFlagWrites_whenFlagsAreExplicitlySet(boolean flag) {
        Fixture fixture = createLegacy(30,false);
        transaction().executeWithoutResult(status -> setFlags(programs.getProgram(fixture.program()),flag));
        assertThat(storedValues(fixture.program())).containsExactly(flag,flag,flag,flag,flag,30);
    }

    static Stream<Arguments> dayWrites() {
        return Stream.of(Arguments.of(null,30),Arguments.of(0,30),Arguments.of(-1,30),Arguments.of(1,1),Arguments.of(45,45));
    }

    @ParameterizedTest @MethodSource("dayWrites")
    void shouldKeepRestrictionDayWrites_whenANewProgramIsInserted(Integer input, Integer expected) {
        Integer id = transaction().execute(status -> {
            Program program=newProgram(); program.setDefaultServiceRestrictionDays(input);
            entityManager.persist(program); entityManager.flush(); return program.getId();
        });
        fixtures.add(new Fixture(id,null));
        assertThat(storedValues(id)).containsExactly(false,false,false,false,true,expected);
    }

    @ParameterizedTest @MethodSource("dayWrites")
    void shouldKeepRestrictionDayWrites_whenTheSetterChangesALegacyProgram(Integer input, Integer expected) {
        Fixture fixture=createLegacy(30,false);
        transaction().executeWithoutResult(status -> programs.getProgram(fixture.program()).setDefaultServiceRestrictionDays(input));
        Object actual=storedValues(fixture.program())[5]; assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest @MethodSource("dayWrites")
    void shouldKeepRestrictionDayWrites_whenAnExplicitlyChangedDetachedProgramIsMerged(Integer input, Integer expected) {
        Fixture fixture=createLegacy(30,false);
        Program detached=transaction().execute(status -> programs.getProgram(fixture.program()));
        detached.setDefaultServiceRestrictionDays(input);
        transaction().executeWithoutResult(status -> entityManager.merge(detached));
        Object actual=storedValues(fixture.program())[5]; assertThat(actual).isEqualTo(expected);
    }
}
