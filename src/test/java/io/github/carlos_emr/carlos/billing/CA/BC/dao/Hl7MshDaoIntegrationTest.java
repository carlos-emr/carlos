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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Msh;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obr;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obx;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.billing.CA.BC.util.PathNetLabResults;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link Hl7MshDao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures.</p>
 * @since 2026-03-07
 */
@DisplayName("Hl7Msh Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class Hl7MshDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7MshDao hl7MshDao;

    @Autowired
    private Hl7ObrDao hl7ObrDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager em;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated ID")
        void shouldPersist_whenValidDataProvided() throws Exception {
            Hl7Msh entity = new Hl7Msh();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            hl7MshDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with matching fields")
        void shouldReturnMatchingEntity_whenFoundById() throws Exception {
            Hl7Msh saved = new Hl7Msh();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setMessageId(9001);
            saved.setSendingApp("TestSendingApp");
            saved.setMessageType("ORU");
            hl7MshDao.persist(saved);

            Hl7Msh found = hl7MshDao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getMessageId()).isEqualTo(9001);
            assertThat(found.getSendingApp()).isEqualTo("TestSendingApp");
            assertThat(found.getMessageType()).isEqualTo("ORU");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenEntityNotFound() throws Exception {
            Hl7Msh found = hl7MshDao.find(-999);
            assertThat(found).isNull();
        }

        @Test
        @Tag("update")
        @DisplayName("should update entity fields after merge")
        void shouldUpdateFields_whenMerged() throws Exception {
            Hl7Msh entity = new Hl7Msh();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setSendingApp("OriginalApp");
            hl7MshDao.persist(entity);

            entity.setSendingApp("UpdatedApp");
            hl7MshDao.merge(entity);
            hl7MshDao.flush();

            Hl7Msh found = hl7MshDao.find(entity.getId());
            assertThat(found.getSendingApp()).isEqualTo("UpdatedApp");
        }
    }

    /**
     * Pins finding 165 (#4552): a PathNet report mixing final and pending OBR results must not be
     * reported as final, which the alphabetical {@code MIN(resultStatus)} did for F with I or P.
     * The aggregate is shared by the {@link Hl7MshDao} patient and provider lists
     * ({@link PathNetLabResults#REPORT_STATUS_HQL}); it is exercised here through
     * {@link Hl7ObrDao#findMinResultStatusByMessageId}, because the Msh list queries select a
     * non-aggregated entity under {@code GROUP BY} that strict H2 rejects (MariaDB accepts it).
     * The list rendering is covered by the {@code pathnet-status} Playwright check.
     */
    @Nested
    @DisplayName("PathNet report status")
    class PathnetReportStatus {

        private int persistReport(int messageId, String... obrStatuses) throws Exception {
            Hl7Pid pid = new Hl7Pid();
            EntityDataGenerator.generateTestDataForModelClass(pid);
            pid.setMessageId(messageId);
            em.persist(pid);

            for (String status : obrStatuses) {
                Hl7Obr obr = new Hl7Obr();
                EntityDataGenerator.generateTestDataForModelClass(obr);
                obr.setPidId(pid.getId());
                obr.setResultStatus(status);
                em.persist(obr);

                Hl7Obx obx = new Hl7Obx();
                EntityDataGenerator.generateTestDataForModelClass(obx);
                obx.setObrId(obr.getId());
                em.persist(obx);
            }
            em.flush();
            return messageId;
        }

        private String reportStatus(int messageId) {
            // The DAO declares List<Object[]> but the single-column select yields scalars; read
            // them as Object so no Object[] cast is inserted.
            List<?> rows = hl7ObrDao.findMinResultStatusByMessageId(messageId);
            assertThat(rows).hasSize(1);
            return (String) rows.get(0);
        }

        @Test
        @Tag("read")
        @DisplayName("should report Final when every result is final")
        void shouldReportFinal_whenAllResultsFinal() throws Exception {
            assertThat(reportStatus(persistReport(900001, "F", "F"))).isEqualTo("F");
        }

        @Test
        @Tag("read")
        @DisplayName("should report preliminary when a final result is mixed with a preliminary one")
        void shouldReportPreliminary_whenFinalMixedWithPreliminary() throws Exception {
            assertThat(reportStatus(persistReport(900002, "F", "P"))).isEqualTo("P");
        }

        @Test
        @Tag("read")
        @DisplayName("should report preliminary when a final result is mixed with an in-process one")
        void shouldReportPreliminary_whenFinalMixedWithInProcess() throws Exception {
            assertThat(reportStatus(persistReport(900003, "F", "I"))).isEqualTo("P");
        }

        @Test
        @Tag("read")
        @DisplayName("should keep corrected when a corrected result is mixed with a final one")
        void shouldKeepCorrected_whenCorrectedMixedWithFinal() throws Exception {
            assertThat(reportStatus(persistReport(900006, "C", "F"))).isEqualTo("C");
        }

        @Test
        @Tag("read")
        @DisplayName("should report preliminary when a corrected result is mixed with a preliminary one")
        void shouldReportPreliminary_whenCorrectedMixedWithPreliminary() throws Exception {
            assertThat(reportStatus(persistReport(900007, "C", "P"))).isEqualTo("P");
        }

        @Test
        @Tag("read")
        @DisplayName("should report preliminary when a final result is mixed with one that has no status")
        void shouldReportPreliminary_whenFinalMixedWithUnreportedStatus() throws Exception {
            assertThat(reportStatus(persistReport(900005, "F", null))).isEqualTo("P");
        }

        @Test
        @Tag("read")
        @DisplayName("should keep the status when every result is preliminary")
        void shouldKeepStatus_whenAllResultsPreliminary() throws Exception {
            assertThat(reportStatus(persistReport(900004, "P"))).isEqualTo("P");
        }
    }
}
