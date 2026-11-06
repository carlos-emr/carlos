// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.on.CML.ABCDParser;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Actual upload service, parser, DAO writes and transaction completion, verified independently. */
@Tag("integration")
@Tag("lab")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LegacyLabUploadDaoIntegrationTest extends CarlosTestBase {
    private final String marker = UUID.randomUUID().toString().substring(0, 8);
    private final String filename = "synthetic-5548-" + marker;
    @Autowired @Qualifier("dataSource") private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;

    @org.junit.jupiter.api.BeforeEach
    void createCoordinationTable() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> em.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate());
    }

    private int cmlPatient;
    private int cmlReport;

    private String content() {
        return "A^70^20261105^12:00^1^1^0^\n"
                + String.join("^", "B", marker, "1", "20261105", "Synthetic", "Fixture", "F", "",
                        "20000101", "F", "000000", "Synthetic", "", "", "", "", "", "", "", "", "", "05 NOV 26", "")
                + "\nC^Chemistry^^^Synthetic test^A^1^9^mmol/L^10^70^Y^\n";
    }

    private FileUploadCheck.StoreOutcome upload(boolean fail) throws Exception {
        byte[] bytes = content().getBytes(StandardCharsets.UTF_8);
        return FileUploadCheck.storeIfNew(filename, () -> new ByteArrayInputStream(bytes), "999998", checksumId -> {
            ABCDParser parser = new ABCDParser();
            parser.parse(new BufferedReader(new StringReader(content())));
            parser.save();
            em.flush();
            assertThat(((Number) em.createNativeQuery("SELECT COUNT(*) FROM labPatientPhysicianInfo WHERE accession_num=?1")
                    .setParameter(1, marker).getSingleResult()).intValue()).isEqualTo(1);
            assertThat(((Number) em.createNativeQuery("SELECT COUNT(*) FROM patientLabRouting p JOIN labPatientPhysicianInfo l"
                    + " ON p.lab_no=l.id WHERE p.lab_type='CML' AND l.accession_num=?1")
                    .setParameter(1, marker).getSingleResult()).intValue()).isEqualTo(1);
            Object[] ids = (Object[]) em.createNativeQuery("SELECT id,labReportInfo_id FROM labPatientPhysicianInfo WHERE accession_num=?1")
                    .setParameter(1, marker).getSingleResult();
            cmlPatient = ((Number) ids[0]).intValue();
            cmlReport = ((Number) ids[1]).intValue();
            var test = em.createQuery("SELECT r FROM LabTestResults r WHERE r.labPatientPhysicianInfoId=?1",
                    io.github.carlos_emr.carlos.commn.model.LabTestResults.class).setParameter(1,cmlPatient).getSingleResult();
            assertThat(test.getUnits()).isEqualTo("mmol/L");
            assertThat(test.getResult()).isEqualTo("10");
            if (fail) throw new IllegalStateException("synthetic failure after lab and routing writes");
            return true;
        });
    }

    @Test
    void shouldCommitParserRowsAndChecksum_andRejectDuplicateUpload() throws Exception {
        assertThat(upload(false)).isEqualTo(FileUploadCheck.StoreOutcome.STORED);
        assertThat(independentCount("SELECT COUNT(*) FROM fileUploadCheck WHERE filename=?", filename)).isEqualTo(1);
        assertThat(independentCount("SELECT COUNT(*) FROM labPatientPhysicianInfo WHERE accession_num=?", marker)).isEqualTo(1);
        assertThat(upload(false)).isEqualTo(FileUploadCheck.StoreOutcome.ALREADY_RECORDED);
        assertThat(independentCount("SELECT COUNT(*) FROM labPatientPhysicianInfo WHERE accession_num=?", marker)).isEqualTo(1);
    }

    @Test
    void shouldRollBackParserRowsRoutingAndChecksum_whenFailureFollowsWrites() throws Exception {
        assertThatThrownBy(() -> upload(true)).isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic failure after lab and routing writes");
        assertThat(independentCount("SELECT COUNT(*) FROM fileUploadCheck WHERE filename=?", filename)).isZero();
        assertThat(independentCount("SELECT COUNT(*) FROM labPatientPhysicianInfo WHERE accession_num=?", marker)).isZero();
        assertThat(independentCount("SELECT COUNT(*) FROM labReportInformation WHERE id=?", Integer.toString(cmlReport))).isZero();
        assertThat(independentCount("SELECT COUNT(*) FROM labTestResults WHERE labPatientPhysicianInfo_id=?", Integer.toString(cmlPatient))).isZero();
        assertThat(independentCount("SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='CML' AND lab_no=?", Integer.toString(cmlPatient))).isZero();
        assertThat(independentCount("SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='CML' AND lab_no=?", Integer.toString(cmlPatient))).isZero();
        // A retry must be accepted, proving that no orphan checksum survived rollback.
        assertThat(upload(false)).isEqualTo(FileUploadCheck.StoreOutcome.STORED);
    }

    private int hl7Segment;

    private FileUploadCheck.StoreOutcome uploadHl7(boolean fail) throws Exception {
        byte[] bytes = ("synthetic HL7 transaction " + marker).getBytes(StandardCharsets.UTF_8);
        return FileUploadCheck.storeIfNew(filename + "-hl7", () -> new ByteArrayInputStream(bytes), "999998", checksum -> {
            var handler = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler.class);
            org.mockito.Mockito.when(handler.getFirstName()).thenReturn("Synthetic");
            org.mockito.Mockito.when(handler.getLastName()).thenReturn("Fixture");
            org.mockito.Mockito.when(handler.getDOB()).thenReturn("2000-01-01");
            org.mockito.Mockito.when(handler.getSex()).thenReturn("F");
            org.mockito.Mockito.when(handler.getHealthNum()).thenReturn("5548000000");
            org.mockito.Mockito.when(handler.getMsgPriority()).thenReturn("");
            org.mockito.Mockito.when(handler.getOrderStatus()).thenReturn("F");
            org.mockito.Mockito.when(handler.getAccessionNum()).thenReturn(marker);
            org.mockito.Mockito.when(handler.getServiceDate()).thenReturn("2026-11-05 12:00:00");
            org.mockito.Mockito.when(handler.getDocNums()).thenReturn(new java.util.ArrayList<>(java.util.List.of("554899")));
            org.mockito.Mockito.when(handler.getHeaders()).thenReturn(new java.util.ArrayList<>(java.util.List.of("Synthetic")));
            var result = new io.github.carlos_emr.carlos.lab.ca.all.upload.RouteReportResults();
            io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader.routeReport(null, "synthetic", handler,
                    new String(bytes, StandardCharsets.UTF_8), checksum, result, "HL7");
            hl7Segment = result.segmentId;
            em.flush();
            assertThat(((Number) em.createNativeQuery("SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=?1")
                    .setParameter(1, hl7Segment).getSingleResult()).intValue()).isEqualTo(1);
            if (fail) throw new IllegalStateException("synthetic failure after HL7 routing");
            return true;
        });
    }

    @Test
    void shouldCommitHl7MessageInfoAndRouting_withTheUploadChecksum() throws Exception {
        assertThat(uploadHl7(false)).isEqualTo(FileUploadCheck.StoreOutcome.STORED);
        assertHl7Rows(1);
        assertThat(uploadHl7(false)).isEqualTo(FileUploadCheck.StoreOutcome.ALREADY_RECORDED);
        assertHl7Rows(1);
    }

    @Test
    void shouldRollBackHl7MessageInfoAndRouting_whenServiceFailsAfterRouting() throws Exception {
        assertThatThrownBy(() -> uploadHl7(true)).isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic failure after HL7 routing");
        assertHl7Rows(0);
    }

    private void assertHl7Rows(int expected) throws Exception {
        assertThat(independentCount("SELECT COUNT(*) FROM fileUploadCheck WHERE filename=?", filename + "-hl7")).isEqualTo(expected);
        for (String table : new String[]{"hl7TextMessage", "hl7TextInfo", "providerLabRouting"}) {
            String key = table.equals("hl7TextMessage") ? "lab_id" : "lab_no";
            assertThat(independentCount("SELECT COUNT(*) FROM " + table + " WHERE " + key + "=?",
                    Integer.toString(hl7Segment))).as(table).isEqualTo(expected);
        }
    }

    private int independentCount(String sql, String value) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); return result.getInt(1); }
        }
    }

    @AfterEach
    void cleanupOwnedRows() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var patients = em.createNativeQuery("SELECT id, labReportInfo_id FROM labPatientPhysicianInfo WHERE accession_num=?1")
                    .setParameter(1, marker).getResultList();
            for (Object value : patients) {
                Object[] ids = (Object[]) value;
                for (String sql : new String[]{"DELETE FROM labTestResults WHERE labPatientPhysicianInfo_id=?1",
                        "DELETE FROM patientLabRouting WHERE lab_type='CML' AND lab_no=?1",
                        "DELETE FROM providerLabRouting WHERE lab_type='CML' AND lab_no=?1",
                        "DELETE FROM providerLabRoutingLock WHERE lab_no=?1",
                        "DELETE FROM labPatientPhysicianInfo WHERE id=?1"}) {
                    em.createNativeQuery(sql).setParameter(1, ids[0]).executeUpdate();
                }
                em.createNativeQuery("DELETE FROM labReportInformation WHERE id=?1").setParameter(1, ids[1]).executeUpdate();
            }
            if (hl7Segment > 0) {
                for (String sql : new String[]{"DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=?1",
                        "DELETE FROM providerLabRoutingLock WHERE lab_no=?1", "DELETE FROM hl7TextInfo WHERE lab_no=?1",
                        "DELETE FROM hl7TextMessage WHERE lab_id=?1"}) {
                    em.createNativeQuery(sql).setParameter(1, hl7Segment).executeUpdate();
                }
            }
            em.createNativeQuery("DELETE FROM fileUploadCheck WHERE filename IN (?1,?2)")
                    .setParameter(1, filename).setParameter(2, filename + "-hl7").executeUpdate();
        });
    }
}
