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
package io.github.carlos_emr.carlos.commn.dao.forms;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link FormsDao} covering lab requisition form queries
 * and parameterized native query execution.
 *
 * <p>Migrated from legacy {@code FormDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see FormsDao
 */
@DisplayName("FormsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("forms")
@Transactional
public class FormsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FormsDao formsDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager em;

    @BeforeEach
    void createFormTables() {
        // FormLabReq07 entity exists in JPA but lacks formCreated/patientName columns.
        // hbm2ddl creates the table from the entity, so we add missing columns.
        em.createNativeQuery(
                "ALTER TABLE formLabReq07 ADD COLUMN IF NOT EXISTS formCreated DATE"
        ).executeUpdate();
        em.createNativeQuery(
                "ALTER TABLE formLabReq07 ADD COLUMN IF NOT EXISTS patientName VARCHAR(255)"
        ).executeUpdate();

        // Create formLabReq10 table (may not have a JPA entity)
        em.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS formLabReq10 (" +
                        "ID INT AUTO_INCREMENT PRIMARY KEY, " +
                        "formCreated DATE, " +
                        "patientName VARCHAR(255), " +
                        "demographic_no INT)"
        ).executeUpdate();
        em.createNativeQuery(
                "ALTER TABLE formLabReq10 ADD COLUMN IF NOT EXISTS formCreated DATE"
        ).executeUpdate();
        em.createNativeQuery(
                "ALTER TABLE formLabReq10 ADD COLUMN IF NOT EXISTS patientName VARCHAR(255)"
        ).executeUpdate();

        // Insert test data into formLabReq07
        em.createNativeQuery(
                "INSERT INTO formLabReq07 (formCreated, patientName, demographic_no) VALUES ('2026-01-15', 'Smith, John', 100)"
        ).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO formLabReq07 (formCreated, patientName, demographic_no) VALUES ('2026-02-20', 'Doe, Jane', 200)"
        ).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO formLabReq07 (formCreated, patientName, demographic_no) VALUES ('2026-03-01', 'Warren, Dennis', 100)"
        ).executeUpdate();

        em.flush();
    }

    @Nested
    @DisplayName("Lab Requisition Form queries")
    class LabRequisitionFormQueries {

        @Test
        @Tag("read")
        @DisplayName("should return all rows from formLabReq07 with id, formCreated and patientName")
        void shouldReturnAllRows_fromFormLabReq07() {
            List<Object[]> results = formsDao.findIdFormCreatedAndPatientNameFromFormLabReq07();
            assertThat(results).hasSize(3);

            // Each row should have 3 columns: ID, formCreated, patientName
            for (Object[] row : results) {
                assertThat(row).hasSize(3);
                assertThat(row[0]).isNotNull(); // ID
                assertThat(row[2]).isNotNull(); // patientName
            }

            assertThat(results).extracting(row -> (String) row[2])
                    .contains("Smith, John", "Doe, Jane", "Warren, Dennis");
        }

        @Test
        @Tag("read")
        @DisplayName("should return formCreated from formLabReq07 by existing ID")
        void shouldReturnFormCreated_fromFormLabReq07ByExistingId() {
            // Get the first inserted ID
            List<Object[]> allRows = formsDao.findIdFormCreatedAndPatientNameFromFormLabReq07();
            Integer firstId = ((Number) allRows.get(0)[0]).intValue();

            List<Object> results = formsDao.findFormCreatedFromFormLabReq07ById(firstId);
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list from formLabReq07 by non-existent ID")
        void shouldReturnEmptyList_fromFormLabReq07ByNonExistentId() {
            List<Object> results = formsDao.findFormCreatedFromFormLabReq07ById(999999);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return rows from formLabReq07 filtered by demographic number")
        void shouldReturnRows_fromFormLabReq07FilteredByDemographicNo() {
            List<Object[]> results = formsDao.findIdFormCreatedAndPatientNameFromFormLabReq07("100");
            assertThat(results).hasSize(2);
            assertThat(results).extracting(row -> (String) row[2])
                    .containsExactlyInAnyOrder("Smith, John", "Warren, Dennis");
        }

        @Test
        @Tag("read")
        @DisplayName("should return all rows when demographic number is null")
        void shouldReturnAllRows_whenDemographicNoIsNull() {
            List<Object[]> results = formsDao.findIdFormCreatedAndPatientNameFromFormLabReq07(null);
            assertThat(results).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Parameterized native query execution")
    class ParameterizedNativeQueries {

        @Test
        @Tag("read")
        @DisplayName("should execute parameterized query with varargs name-value pairs")
        void shouldExecuteParameterizedQuery_withVarargs() {
            List<Object[]> results = formsDao.runParameterizedNativeQuery(
                    "SELECT ID, patientName FROM formLabReq07 WHERE demographic_no = :demoNo",
                    "demoNo", 100
            );
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("read")
        @DisplayName("should execute parameterized query with Map parameters")
        void shouldExecuteParameterizedQuery_withMapParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("demoNo", 200);
            List<Object[]> results = formsDao.runParameterizedNativeQuery(
                    "SELECT ID, patientName FROM formLabReq07 WHERE demographic_no = :demoNo",
                    params
            );
            assertThat(results).hasSize(1);
            assertThat((String) results.get(0)[1]).isEqualTo("Doe, Jane");
        }

        @Test
        @Tag("read")
        @DisplayName("should wrap single-column results in Object array when using Map params")
        void shouldWrapSingleColumnResults_inObjectArray() {
            Map<String, Object> params = new HashMap<>();
            params.put("demoNo", 100);
            List<Object[]> results = formsDao.runParameterizedNativeQuery(
                    "SELECT patientName FROM formLabReq07 WHERE demographic_no = :demoNo",
                    params
            );
            assertThat(results).hasSize(2);
            // Single column results should be wrapped in Object[]
            for (Object[] row : results) {
                assertThat(row).hasSize(1);
                assertThat(row[0]).isInstanceOf(String.class);
            }
        }

        @Test
        @Tag("read")
        @DisplayName("should throw exception for odd number of varargs parameters")
        void shouldThrowException_forOddNumberOfVarargsParams() {
            assertThatThrownBy(() ->
                    formsDao.runParameterizedNativeQuery(
                            "SELECT * FROM formLabReq07",
                            "onlyName"
                    )
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name-value pairs");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list for query with no matching rows using Map params")
        void shouldReturnEmptyList_forNoMatchingRowsWithMapParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("demoNo", 999999);
            List<Object[]> results = formsDao.runParameterizedNativeQuery(
                    "SELECT ID, patientName FROM formLabReq07 WHERE demographic_no = :demoNo",
                    params
            );
            assertThat(results).isEmpty();
        }
    }
}
