/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/** Commits and reloads the real routing rows; only the version lookup is synthetic. */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LabAcknowledgementAuditIntegrationTest extends CarlosTestBase {
    private static final int OLDER = 9837800;
    private static final int MIDDLE = OLDER + 1;
    private static final int REVIEWED = OLDER + 2;
    private static final int NEWER = OLDER + 3;
    private static final Date ACK_TIME = new Date(1234567890000L);
    @Autowired private ProviderLabRoutingDao routes;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    private TransactionTemplate tx;
    private Object originalRoutes;

    @BeforeEach
    void seedRouting() {
        // Other test classes may have initialized this legacy static field with a mock.
        originalRoutes = ReflectionTestUtils.getField(CommonLabResultData.class, "providerLabRoutingDao");
        ReflectionTestUtils.setField(CommonLabResultData.class, "providerLabRoutingDao", routes);
        tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.executeWithoutResult(status -> {
            em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
            add(OLDER, "REVIEWER", "A");
            add(OLDER, "REVIEWER", "N"); // Legacy duplicate must still be filed.
            add(MIDDLE, "REVIEWER", "N");
            add(REVIEWED, "REVIEWER", "N");
            add(NEWER, "REVIEWER", "N");
            add(OLDER, "OTHER", "N");
            add(MIDDLE, "OTHER", "A");
        });
    }

    private void add(int lab, String provider, String status) {
        ProviderLabRoutingModel row = new ProviderLabRoutingModel();
        row.setLabNo(lab);
        row.setLabType("HL7");
        row.setProviderNo(provider);
        row.setStatus(status);
        row.setComment("Original review");
        row.setTimestamp(ACK_TIME);
        routes.persist(row);
    }

    @AfterEach
    void removeRouting() {
        try {
            tx.executeWithoutResult(status -> {
                em.createQuery("delete from ProviderLabRoutingModel where labNo between ?1 and ?2")
                        .setParameter(1, OLDER).setParameter(2, NEWER).executeUpdate();
                em.createNativeQuery("delete from providerLabRoutingLock where lab_no between ?1 and ?2")
                        .setParameter(1, OLDER).setParameter(2, NEWER).executeUpdate();
            });
        } finally {
            ReflectionTestUtils.setField(CommonLabResultData.class, "providerLabRoutingDao", originalRoutes);
        }
    }

    @Test
    void shouldKeepEarlierReviewAudit_whenAcknowledgingNewerVersion() {
        try (var hl7 = mockStatic(Hl7textResultsData.class)) {
            hl7.when(() -> Hl7textResultsData.getMatchingLabs(String.valueOf(REVIEWED)))
                    .thenReturn(OLDER + "," + MIDDLE + "," + REVIEWED + "," + NEWER);
            assertThat(CommonLabResultData.acknowledgeReport(REVIEWED, "REVIEWER", "New review", "HL7", false, null))
                    .isEqualTo(3);
            // A repeat cannot clear more NEW rows or replace the older acknowledgement.
            assertThat(CommonLabResultData.acknowledgeReport(REVIEWED, "REVIEWER", "New review", "HL7", false, null))
                    .isZero();
        }
        tx.executeWithoutResult(status -> {
            var older = routes.findByLabNoAndLabTypeAndProviderNo(OLDER, "HL7", "REVIEWER");
            assertThat(older).extracting(ProviderLabRoutingModel::getStatus).containsExactlyInAnyOrder("A", "F");
            var acknowledged = older.stream().filter(row -> "A".equals(row.getStatus())).findFirst().orElseThrow();
            assertThat(acknowledged.getTimestamp().getTime()).isEqualTo(ACK_TIME.getTime());
            assertThat(acknowledged.getComment()).isEqualTo("Original review");
            assertThat(routes.findByLabNoAndLabTypeAndProviderNo(MIDDLE, "HL7", "REVIEWER").getFirst().getStatus()).isEqualTo("F");
            var reviewed = routes.findByLabNoAndLabTypeAndProviderNo(REVIEWED, "HL7", "REVIEWER").getFirst();
            assertThat(reviewed.getStatus()).isEqualTo("A");
            assertThat(reviewed.getComment()).isEqualTo("New review");
            assertThat(reviewed.getTimestamp()).isAfter(ACK_TIME);
            assertUnchanged(NEWER, "REVIEWER", "N");
            assertUnchanged(OLDER, "OTHER", "N");
            assertUnchanged(MIDDLE, "OTHER", "A");
        });
    }

    private void assertUnchanged(int lab, String provider, String expectedStatus) {
        var row = routes.findByLabNoAndLabTypeAndProviderNo(lab, "HL7", provider).getFirst();
        assertThat(row.getStatus()).isEqualTo(expectedStatus);
        assertThat(row.getComment()).isEqualTo("Original review");
        assertThat(row.getTimestamp().getTime()).isEqualTo(ACK_TIME.getTime());
    }
}
