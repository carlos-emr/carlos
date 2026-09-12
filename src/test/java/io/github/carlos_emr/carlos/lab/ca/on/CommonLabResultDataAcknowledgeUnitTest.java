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
package io.github.carlos_emr.carlos.lab.ca.on;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.PathnetResultsData;
import io.github.carlos_emr.carlos.mds.data.MDSResultsData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Covers which lab versions an acknowledgement files.
 *
 * <p>The inbox collapses a lab's version chain (versions sharing an accession number) to one row
 * for the newest version. Acknowledging without filing the older versions leaves them at status
 * {@code N}, the collapsed row re-appears pointing at the previous version, and the lab looks to
 * the clinician like it was never acknowledged — the alpha-tester report this covers.
 *
 * @since 2026-09-06
 */
@DisplayName("Lab acknowledgement version selection")
@Tag("unit")
@Tag("lab")
class CommonLabResultDataAcknowledgeUnitTest extends CarlosUnitTestBase {

    /**
     * CommonLabResultData and Hl7textResultsData both resolve DAOs in their static initializers;
     * register them so referencing either class does not blow up outside a Spring context.
     */
    private void registerStaticInitializerMocks() {
        org.springframework.transaction.PlatformTransactionManager transactions =
                createAndRegisterMock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(
                new org.springframework.transaction.support.SimpleTransactionStatus());
        registerMock(OscarLogDao.class, mock(OscarLogDao.class));
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(MeasurementsDeletedDao.class, mock(MeasurementsDeletedDao.class));
        registerMock(MeasurementDao.class, mock(MeasurementDao.class));
        registerMock(MeasurementsExtDao.class, mock(MeasurementsExtDao.class));
        registerMock(MeasurementMapDao.class, mock(MeasurementMapDao.class));
        registerMock(ConsultDocsDao.class, mock(ConsultDocsDao.class));
        registerMock(ConsultResponseDocDao.class, mock(ConsultResponseDocDao.class));
        registerMock(Hl7TextInfoDao.class, mock(Hl7TextInfoDao.class));
        registerMock(Hl7TextMessageDao.class, mock(Hl7TextMessageDao.class));
        registerMock(EFormDocsDao.class, mock(EFormDocsDao.class));
        Mockito.reset(staticRoutingDao());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("should atomically commit the whole lab chain or roll it back on a later failure")
    void shouldRollbackWholeChain_whenLaterVersionWriteFails(boolean fail) {
        registerStaticInitializerMocks();
        org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:lab-chain-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        registerMock(org.springframework.transaction.PlatformTransactionManager.class,
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        try (MockedStatic<CommonLabResultData> common = mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7 = mockStatic(Hl7textResultsData.class)) {
            jdbc.execute("CREATE TABLE routing(id INT PRIMARY KEY, status CHAR(1))");
            jdbc.execute("INSERT INTO routing VALUES(169,'N'),(170,'N'),(171,'N')");
            hl7.when(() -> Hl7textResultsData.getMatchingLabs("171")).thenReturn("169,170,171");
            when(staticRoutingDao().transitionNewRoutingRows(anyInt(), anyString(), anyString(), anyChar()))
                    .thenAnswer(call -> jdbc.update("UPDATE routing SET status=? WHERE id=? AND status='N'",
                            String.valueOf((char) call.getArgument(3)), call.getArgument(0, Integer.class)));
            common.when(() -> CommonLabResultData.updateReportStatus(anyInt(), anyString(), anyChar(), any(), any(), anyBoolean()))
                    .thenAnswer(call -> {
                        int id = call.getArgument(0);
                        jdbc.update("UPDATE routing SET status=? WHERE id=?", String.valueOf((char) call.getArgument(2)), id);
                        if (fail && id == 170) {
                            throw new IllegalStateException("injected mid-chain failure");
                        }
                        return true;
                    });
            if (fail) {
                assertThatThrownBy(() -> CommonLabResultData.acknowledgeReport(171, "999998", "", "HL7", false, null))
                        .isInstanceOf(IllegalStateException.class).hasMessage("injected mid-chain failure");
                assertThat(jdbc.queryForList("SELECT status FROM routing ORDER BY id", String.class)).containsExactly("N", "N", "N");
            } else {
                assertThat(CommonLabResultData.acknowledgeReport(171, "999998", "", "HL7", false, null)).isEqualTo(3);
                assertThat(jdbc.queryForList("SELECT status FROM routing ORDER BY id", String.class)).containsExactly("F", "F", "A");
            }
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    @Test
    @DisplayName("should mark the reviewed version acknowledged and file each earlier one")
    void shouldWriteEachVersionStatus_whenAcknowledgingReport() {
        registerStaticInitializerMocks();

        // The routine under test runs for real; only the per-row write is stubbed, so this
        // pins the statuses and the ids actually written — a reversed, truncated or wrongly
        // lettered filing loop fails here rather than reaching a clinician's inbox.
        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("171")).thenReturn("169,170,171");
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any())).thenReturn(true);

            CommonLabResultData.acknowledgeReport(171, "999998", "Reviewed", "HL7", false, "169,170,171");

            org.mockito.InOrder locks = Mockito.inOrder(staticRoutingDao());
            locks.verify(staticRoutingDao()).lockRoutingReport(169);
            locks.verify(staticRoutingDao()).lockRoutingReport(170);
            locks.verify(staticRoutingDao()).lockRoutingReport(171);
            locks.verify(staticRoutingDao()).transitionNewRoutingRows(169, "HL7", "999998", 'F');

            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    171, "999998", 'A', "Reviewed", "HL7", false));
            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    169, "999998", 'F', "", "HL7"));
            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    170, "999998", 'F', "", "HL7"));
        }
    }

    @Test
    @DisplayName("should report one cleared routing row for every version it took out of NEW")
    void shouldReportClearedRowCount_forAMultiVersionChain() {
        registerStaticInitializerMocks();

        // The inbox counters count providerLabRouting rows — one per lab VERSION — while the
        // list collapses a chain to a single row. The browser cannot see the chain on the
        // macro path, so this number is the only thing that lets it move the badge by the
        // amount the next page load will compute for itself.
        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("171")).thenReturn("169,170,171");
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any())).thenReturn(true);
            // Every version of this chain is still sitting in the provider's inbox.
            when(staticRoutingDao().transitionNewRoutingRows(
                    anyInt(), anyString(), anyString(), anyChar())).thenReturn(1);

            int cleared = CommonLabResultData.acknowledgeReport(
                    171, "999998", "Reviewed", "HL7", false, "169,170,171");

            assertThat(cleared).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("should not count a version of the chain that somebody had already filed")
    void shouldSkipAlreadyFiledVersions_whenCountingClearedRows() {
        registerStaticInitializerMocks();

        // Only rows that were NEW are in a total the inbox badge is counting. Counting one per
        // version regardless made the badge read low on a partly-filed chain, and the clinician
        // saw a figure that only a full page reload put right.
        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("171")).thenReturn("169,170,171");
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any())).thenReturn(true);
            when(staticRoutingDao().transitionNewRoutingRows(
                    anyInt(), anyString(), anyString(), anyChar())).thenReturn(1);
            // 169 was filed by hand earlier, so it is not in the badge's total any more.
            when(staticRoutingDao().transitionNewRoutingRows(
                    eq(169), anyString(), anyString(), anyChar())).thenReturn(0);

            int cleared = CommonLabResultData.acknowledgeReport(
                    171, "999998", "Reviewed", "HL7", false, "169,170,171");

            assertThat(cleared).isEqualTo(2);
        }
    }

    private static ProviderLabRoutingModel routingRow(String status) {
        ProviderLabRoutingModel row = new ProviderLabRoutingModel();
        row.setStatus(status);
        return row;
    }

    @Test
    @DisplayName("should count a NEW routing row only once across simultaneous acknowledgements")
    void shouldCountOnce_whenAcknowledgementsRace() throws Exception {
        registerStaticInitializerMocks();
        org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:lab-race-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        registerMock(org.springframework.transaction.PlatformTransactionManager.class,
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE routing(id INT PRIMARY KEY, status CHAR(1))");
        jdbc.execute("INSERT INTO routing VALUES(170,'N')");
        java.util.concurrent.CyclicBarrier bothReady = new java.util.concurrent.CyclicBarrier(2);
        ProviderLabRoutingDao dao = staticRoutingDao();
        when(dao.transitionNewRoutingRows(170, "DOC", "999998", 'A')).thenAnswer(call -> {
            bothReady.await(10, java.util.concurrent.TimeUnit.SECONDS);
            return jdbc.update("UPDATE routing SET status='A' WHERE id=170 AND status='N'");
        });
        // Normal metadata writes follow the real atomic transition in each transaction.
        when(dao.findRoutingForUpdate(170, "DOC", "999998"))
                .thenAnswer(call -> List.of(routingRow(jdbc.queryForObject("SELECT status FROM routing WHERE id=170", String.class))));
        var manager = io.github.carlos_emr.carlos.utility.SpringUtils.getBean(
                org.springframework.transaction.PlatformTransactionManager.class);
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> acknowledge = () -> {
                // SpringUtils mocking is thread-local; give each worker the same real manager.
                try (MockedStatic<io.github.carlos_emr.carlos.utility.SpringUtils> spring =
                             mockStatic(io.github.carlos_emr.carlos.utility.SpringUtils.class)) {
                    spring.when(() -> io.github.carlos_emr.carlos.utility.SpringUtils.getBean(
                            org.springframework.transaction.PlatformTransactionManager.class)).thenReturn(manager);
                    return CommonLabResultData.acknowledgeReport(170, "999998", "", "DOC", false, null);
                }
            };
            var first = workers.submit(acknowledge);
            var second = workers.submit(acknowledge);
            assertThat(List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(1, 0);
            assertThat(jdbc.queryForObject("SELECT status FROM routing WHERE id=170", String.class)).isEqualTo("A");
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
            jdbc.execute("SHUTDOWN");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("should create only one missing routing row across simultaneous acknowledgements")
    void shouldCreateMissingRoutingOnce_whenAcknowledgementsRace(boolean wholeChain) throws Exception {
        registerStaticInitializerMocks();
        org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:lab-missing-race-" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE routing(id INT AUTO_INCREMENT PRIMARY KEY, lab_no INT, status CHAR(1), comment VARCHAR(255))");
        jdbc.execute("CREATE TABLE providerLabRoutingLock(lab_no INT PRIMARY KEY)");
        java.util.concurrent.CountDownLatch missingReaders = new java.util.concurrent.CountDownLatch(2);
        ProviderLabRoutingDao dao = staticRoutingDao();
        Mockito.doAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            jdbc.update("INSERT INTO providerLabRoutingLock (lab_no) VALUES (?) ON DUPLICATE KEY UPDATE lab_no=VALUES(lab_no)",
                    call.getArgument(0, Integer.class));
            return null;
        }).when(dao).lockRoutingReport(anyInt());
        when(dao.findRoutingForUpdate(anyInt(), eq("DOC"), eq("999998"))).thenAnswer(call -> {
            List<ProviderLabRoutingModel> rows = jdbc.query("SELECT id,status,comment FROM routing WHERE lab_no=? FOR UPDATE", (rs, index) -> {
                ProviderLabRoutingModel row = routingRow(rs.getString("status"));
                org.springframework.test.util.ReflectionTestUtils.setField(row, "id", rs.getInt("id"));
                row.setComment(rs.getString("comment"));
                return row;
            }, call.getArgument(0, Integer.class));
            if (rows.isEmpty()) {
                missingReaders.countDown();
                // Before serialization both readers observe absence. After serialization the
                // first reader times out here; the next reader sees its committed insertion.
                missingReaders.await(300, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            return rows;
        });
        Mockito.doAnswer(call -> {
            ProviderLabRoutingModel row = call.getArgument(0);
            jdbc.update("INSERT INTO routing(lab_no,status,comment) VALUES (?,?,?)", row.getLabNo(), row.getStatus(), row.getComment());
            return null;
        }).when(dao).persist(any(ProviderLabRoutingModel.class));
        Mockito.doAnswer(call -> {
            ProviderLabRoutingModel row = call.getArgument(0);
            jdbc.update("UPDATE routing SET status=?,comment=? WHERE id=?", row.getStatus(), row.getComment(), row.getId());
            return null;
        }).when(dao).merge(any(ProviderLabRoutingModel.class));
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> acknowledge = () -> {
                try (MockedStatic<io.github.carlos_emr.carlos.utility.SpringUtils> spring =
                             mockStatic(io.github.carlos_emr.carlos.utility.SpringUtils.class);
                     MockedStatic<CommonLabResultData> common = mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS)) {
                    spring.when(() -> io.github.carlos_emr.carlos.utility.SpringUtils.getBean(
                            org.springframework.transaction.PlatformTransactionManager.class)).thenReturn(manager);
                    if (wholeChain) {
                        // Exercise materialization of an older routing row too, independently
                        // of the already separately tested server-side version-chain lookup.
                        common.when(() -> CommonLabResultData.olderVersionsOf(170, "DOC", null)).thenReturn(List.of(169));
                        assertThat(CommonLabResultData.acknowledgeReport(170, "999998", "Keep [clinical] $1", "DOC", false, null))
                                .isZero();
                        return true;
                    }
                    return CommonLabResultData.updateReportStatus(170, "999998", 'A', "Keep [clinical] $1", "DOC");
                }
            };
            var first = workers.submit(acknowledge);
            var second = workers.submit(acknowledge);
            assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing", Integer.class)).isEqualTo(wholeChain ? 2 : 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE lab_no=170", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT comment FROM routing WHERE lab_no=170", String.class)).isEqualTo("Keep [clinical] $1");
            if (wholeChain) {
                assertThat(jdbc.queryForObject("SELECT status FROM routing WHERE lab_no=169", String.class)).isEqualTo("F");
            }
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
            jdbc.execute("SHUTDOWN");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void shouldTreatCommentsAsLiteralText_andHonorSkipCommentOnUpdate(boolean skip) {
        registerStaticInitializerMocks();
        ProviderLabRoutingDao dao = staticRoutingDao();
        Mockito.reset(dao);
        ProviderLabRoutingModel row = routingRow("N");
        row.setComment("Prior [review] $1 (");
        when(dao.findRoutingForUpdate(42, "DOC", "999998"))
                .thenReturn(List.of(row));
        String revised = "Updated [review] $2";
        CommonLabResultData.updateReportStatus(42, "999998", 'A', revised, "DOC", skip);
        assertThat(row.getComment()).isEqualTo(skip ? "Prior [review] $1 (" : revised);
        assertThat(row.getStatus()).isEqualTo("A");
        Mockito.verify(dao).merge(row);
    }

    /**
     * The ProviderLabRoutingDao instance CommonLabResultData actually holds.
     *
     * <p>It resolves its DAOs in a static initializer, so the field binds to whichever mock was
     * registered when the class first loaded in this Surefire fork — not to whatever a later
     * registerMock() call supplies. Reading the field is the only way to stub the instance the
     * production code will really use.
     */
    private static ProviderLabRoutingDao staticRoutingDao() {
        try {
            java.lang.reflect.Field field =
                    CommonLabResultData.class.getDeclaredField("providerLabRoutingDao");
            field.setAccessible(true);
            return (ProviderLabRoutingDao) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not read the bound routing DAO", e);
        }
    }

    @Test
    @DisplayName("should report a single cleared routing row for a lab with no earlier version")
    void shouldReportOneClearedRow_forASingleVersionLab() {
        registerStaticInitializerMocks();

        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("170");
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);
            when(staticRoutingDao().transitionNewRoutingRows(
                    anyInt(), anyString(), anyString(), anyChar())).thenReturn(1);

            int cleared = CommonLabResultData.acknowledgeReport(170, "999998", "", "HL7", true, "170");

            assertThat(cleared).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("should write nothing when the version chain cannot be resolved")
    void shouldWriteNothing_whenChainLookupFails() {
        registerStaticInitializerMocks();

        // Resolving the chain hits the database. If that fails after the reviewed row is
        // already stamped, the caller reports a failure while the acknowledgement is
        // half-applied — lab acknowledged, older versions still NEW, collapsed row back in
        // the inbox. Failing before the first write leaves nothing behind.
        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("171"))
                    .thenThrow(new IllegalStateException("database unavailable"));
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);

            assertThatThrownBy(() -> CommonLabResultData.acknowledgeReport(
                    171, "999998", "Reviewed", "HL7", false, "169,170,171"))
                    .isInstanceOf(IllegalStateException.class);

            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean()), never());
        }
    }

    @Test
    @DisplayName("should file nothing beyond the acknowledged version when it is the only one")
    void shouldWriteOnlyTheAcknowledgedVersion_forSingleVersionReport() {
        registerStaticInitializerMocks();

        try (MockedStatic<CommonLabResultData> commonLabResultData =
                     mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
             MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("170");
            commonLabResultData.when(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), anyChar(), any(), any(), anyBoolean())).thenReturn(true);

            CommonLabResultData.acknowledgeReport(170, "999998", "", "HL7", true, "170");

            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    170, "999998", 'A', "", "HL7", true));
            commonLabResultData.verify(() -> CommonLabResultData.updateReportStatus(
                    anyInt(), anyString(), eq('F'), any(), any()), never());
        }
    }

    @Test
    @DisplayName("should file the earlier versions when the newest version of a lab is acknowledged")
    void shouldSelectEarlierVersions_whenAcknowledgingNewestVersion() {
        registerStaticInitializerMocks();

        try (MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("171")).thenReturn("169,170,171");

            assertThat(CommonLabResultData.olderVersionsOf(171, "HL7", "169,170,171"))
                    .containsExactly(169, 170);
        }
    }

    @Test
    @DisplayName("should leave a later corrected version alone when an earlier version is acknowledged")
    void shouldExcludeLaterVersions_whenAcknowledgingEarlierVersion() {
        registerStaticInitializerMocks();

        try (MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("169,170,171");

            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", "169,170,171"))
                    .containsExactly(169);
        }
    }

    @Test
    @DisplayName("should derive the HL7 version chain server side and ignore the posted one")
    void shouldIgnorePostedChain_forHl7Labs() {
        registerStaticInitializerMocks();

        try (MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("169,170");

            // Every id in the chain becomes a write to another lab's routing row, so for HL7 it
            // comes from the accession number, never from the browser. A macro that posted no
            // chain, and a forged one naming unrelated labs, both file this lab's own versions
            // and nothing else.
            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", null)).containsExactly(169);
            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", "900,901")).containsExactly(169);
            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", "900,901,170"))
                    .as("a forged chain must not get unrelated labs filed")
                    .containsExactly(169);
        }
    }

    @Test
    @DisplayName("should file nothing for a non-HL7 report whose posted chain does not describe it")
    void shouldFileNothing_forNonHl7ReportWithoutUsableChain() {
        registerStaticInitializerMocks();

        // Documents have no accession-number chain to derive, so they fall back to the posted
        // value; one that does not describe this document files nothing rather than guess —
        // and must not throw the way the old index walk did.
        assertThat(CommonLabResultData.olderVersionsOf(42, "DOC", null)).isEmpty();
        assertThat(CommonLabResultData.olderVersionsOf(42, "DOC", "900,901")).isEmpty();
    }

    @Test
    @DisplayName("should file nothing when the acknowledged lab is the only version in its chain")
    void shouldFileNothing_forSingleVersionLab() {
        registerStaticInitializerMocks();

        try (MockedStatic<Hl7textResultsData> hl7Results = mockStatic(Hl7textResultsData.class)) {
            hl7Results.when(() -> Hl7textResultsData.getMatchingLabs("170")).thenReturn("170");

            assertThat(CommonLabResultData.olderVersionsOf(170, "HL7", "170")).isEmpty();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"DOC", "HRM", "Epsilon", "unknown"})
    void shouldNeverFilePostedUnrelatedRecords_forTypesWithoutVersionLookup(String type) {
        registerStaticInitializerMocks();
        assertThat(CommonLabResultData.olderVersionsOf(42, type, "900,901,42")).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MDS", "CML"})
    void shouldDeriveLegacyOntarioVersions_onTheServer(String type) {
        registerStaticInitializerMocks();
        try (MockedConstruction<MDSResultsData> constructed = Mockito.mockConstruction(
                MDSResultsData.class, (mock, context) -> {
                    when(mock.getMatchingLabs("42")).thenReturn("40,41,42,43");
                    when(mock.getMatchingCMLLabs("42")).thenReturn("40,41,42,43");
                })) {
            assertThat(CommonLabResultData.olderVersionsOf(42, type, "900,901,42"))
                    .containsExactly(40, 41);
            assertThat(constructed.constructed()).hasSize(1);
            if ("MDS".equals(type)) {
                Mockito.verify(constructed.constructed().get(0)).getMatchingLabs("42");
            } else {
                Mockito.verify(constructed.constructed().get(0)).getMatchingCMLLabs("42");
            }
        }
    }

    @Test
    void shouldDerivePathnetVersions_onTheServer() {
        registerStaticInitializerMocks();
        try (MockedConstruction<PathnetResultsData> constructed = Mockito.mockConstruction(
                PathnetResultsData.class, (mock, context) ->
                        when(mock.getMatchingLabs("42")).thenReturn("40,41,42,43"))) {
            assertThat(CommonLabResultData.olderVersionsOf(42, "BCP", "900,901,42"))
                    .containsExactly(40, 41);
            Mockito.verify(constructed.constructed().get(0)).getMatchingLabs("42");
        }
    }
}
