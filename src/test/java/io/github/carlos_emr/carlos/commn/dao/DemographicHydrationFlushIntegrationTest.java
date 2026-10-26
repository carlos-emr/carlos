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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Legacy province and birth values keep their stored bytes through actual read commits while
 * their display getters and explicit writes retain the existing normalization contract.
 * @since 2026-10-26
 */
@Tag("integration") @Tag("dao") @Tag("demographic")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemographicHydrationFlushIntegrationTest extends CarlosTestBase {
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private DemographicDao demographicDao;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    enum Column { province, month_of_birth, date_of_birth, year_of_birth }

    static Stream<Arguments> values() {
        return Stream.of(Arguments.of(Column.province," on ","ON"), Arguments.of(Column.province,"   ",null),
                Arguments.of(Column.province,"",null), Arguments.of(Column.month_of_birth,"",null),
                Arguments.of(Column.month_of_birth," ",null), Arguments.of(Column.date_of_birth,"",null),
                Arguments.of(Column.date_of_birth," ",null), Arguments.of(Column.year_of_birth,"",null),
                Arguments.of(Column.year_of_birth,"   ",null));
    }

    @ParameterizedTest @MethodSource("values")
    void shouldPreserveRawDatabaseValues_whenALegacyPatientIsRead(Column column, String raw, String display) {
        var tx = new TransactionTemplate(transactions);
        Integer id = tx.execute(status -> {
            var patient = new Demographic(); patient.setFirstName("FAKE-Hydration"); patient.setLastName("FAKE-Probe");
            patient.setProviderNo("999998"); patient.setHcType("ON"); patient.setPatientStatus("AC"); patient.setRosterStatus("NR");
            patient.setYearOfBirth("1980"); patient.setMonthOfBirth("01"); patient.setDateOfBirth("02");
            patient.setSex("F"); patient.setPrefName(""); entityManager.persist(patient); entityManager.flush();
            entityManager.createNativeQuery("UPDATE demographic SET "+column.name()+"=:raw WHERE demographic_no=:id")
                    .setParameter("raw",raw).setParameter("id",patient.getDemographicNo()).executeUpdate();
            entityManager.clear(); return patient.getDemographicNo();
        });
        try {
            tx.executeWithoutResult(status -> {
                Demographic patient = demographicDao.getDemographicById(id);
                String actual = switch(column) {
                    case province -> patient.getProvince(); case month_of_birth -> patient.getMonthOfBirth();
                    case date_of_birth -> patient.getDateOfBirth(); case year_of_birth -> patient.getYearOfBirth();
                };
                assertThat(actual).isEqualTo(display);
                if (column != Column.province) assertThat(patient.getBirthDay()).isNull();
            });
            Object actual = tx.execute(status -> entityManager.createNativeQuery(
                    "SELECT "+column.name()+" FROM demographic WHERE demographic_no=:id").setParameter("id",id).getSingleResult());
            assertThat(actual).isEqualTo(raw);
        } finally {
            tx.executeWithoutResult(status -> entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no=:id")
                    .setParameter("id",id).executeUpdate());
        }
    }

    @Test
    void shouldPreservePaddedBirthParts_whenTheBirthdayIsRead() {
        withPatient(id -> {
            var tx = new TransactionTemplate(transactions);
            tx.executeWithoutResult(status -> entityManager.createNativeQuery(
                    "UPDATE demographic SET year_of_birth='1980',month_of_birth=' 1',date_of_birth='2 ' WHERE demographic_no=:id")
                    .setParameter("id",id).executeUpdate());
            tx.executeWithoutResult(status -> {
                Demographic patient = demographicDao.getDemographicById(id);
                assertThat(patient.getYearOfBirth()).isEqualTo("1980");
                assertThat(patient.getMonthOfBirth()).isEqualTo("1");
                assertThat(patient.getDateOfBirth()).isEqualTo("2");
                var birthday = patient.getBirthDay();
                assertThat(birthday.get(java.util.Calendar.YEAR)).isEqualTo(1980);
                assertThat(birthday.get(java.util.Calendar.MONTH)).isEqualTo(java.util.Calendar.JANUARY);
                assertThat(birthday.get(java.util.Calendar.DAY_OF_MONTH)).isEqualTo(2);
            });
            assertThat(storedBirthParts(id)).containsExactly("1980"," 1","2 ");
        });
    }

    static Stream<Arguments> writeValues() {
        return Stream.of(Arguments.of(null,null,null,null,null,null,null,null),
                Arguments.of("","","","",null,null,null,null),
                Arguments.of("   "," "," ","   ",null,null,null,null),
                Arguments.of(" on "," 1","2 ","1980","ON","1980","1","2"),
                Arguments.of("BC","12","31","2001","BC","2001","12","31"));
    }

    @ParameterizedTest @MethodSource("writeValues")
    void shouldKeepExistingSetterNormalization_whenProvinceAndBirthPartsAreWritten(String province, String month, String day,
            String year, String expectedProvince, String expectedYear, String expectedMonth, String expectedDay) {
        withPatient(id -> {
            var tx = new TransactionTemplate(transactions);
            tx.executeWithoutResult(status -> {
                Demographic patient = demographicDao.getDemographicById(id);
                patient.setProvince(province); patient.setMonthOfBirth(month); patient.setDateOfBirth(day); patient.setYearOfBirth(year);
            });
            Object[] actual = tx.execute(status -> (Object[]) entityManager.createNativeQuery(
                    "SELECT province,year_of_birth,month_of_birth,date_of_birth FROM demographic WHERE demographic_no=:id")
                    .setParameter("id",id).getSingleResult());
            assertThat(actual).containsExactly(expectedProvince, expectedYear, expectedMonth, expectedDay);
        });
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldKeepBirthdayWrites_whenTheCalendarSetterIsUsed(boolean absent) {
        withPatient(id -> {
            var tx = new TransactionTemplate(transactions);
            tx.executeWithoutResult(status -> demographicDao.getDemographicById(id).setBirthDay(
                    absent ? null : new java.util.GregorianCalendar(2001,java.util.Calendar.JULY,9)));
            assertThat(storedBirthParts(id)).containsExactly(absent ? new Object[]{null,null,null} : new Object[]{"2001","07","09"});
        });
    }

    private Object[] storedBirthParts(Integer id) {
        return new TransactionTemplate(transactions).execute(status -> (Object[]) entityManager.createNativeQuery(
                "SELECT year_of_birth,month_of_birth,date_of_birth FROM demographic WHERE demographic_no=:id")
                .setParameter("id",id).getSingleResult());
    }

    private void withPatient(java.util.function.Consumer<Integer> operation) {
        var tx = new TransactionTemplate(transactions);
        Integer id = tx.execute(status -> {
            var patient = new Demographic(); patient.setFirstName("FAKE-Hydration"); patient.setLastName("FAKE-Write");
            patient.setProviderNo("999998"); patient.setHcType("ON"); patient.setSex("F"); patient.setPrefName("");
            entityManager.persist(patient); entityManager.flush(); return patient.getDemographicNo();
        });
        try { operation.accept(id); }
        finally {
            tx.executeWithoutResult(status -> entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no=:id")
                    .setParameter("id",id).executeUpdate());
        }
    }
}
