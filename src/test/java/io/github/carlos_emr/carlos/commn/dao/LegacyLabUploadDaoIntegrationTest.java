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
        // A retry must be accepted, proving that no orphan checksum survived rollback.
        assertThat(upload(false)).isEqualTo(FileUploadCheck.StoreOutcome.STORED);
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
            em.createNativeQuery("DELETE FROM fileUploadCheck WHERE filename=?1").setParameter(1, filename).executeUpdate();
        });
    }
}
