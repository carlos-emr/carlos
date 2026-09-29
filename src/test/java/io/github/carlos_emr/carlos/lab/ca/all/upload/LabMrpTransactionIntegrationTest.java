/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.upload;

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.lab.ForwardingRules;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real routing persistence and commits, with synthetic forwarding rules. */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LabMrpTransactionIntegrationTest extends CarlosTestBase {
    private static final int REPORT = 9834000;
    @Autowired private ProviderLabRoutingDao routes;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    private TransactionTemplate tx;
    private MockedConstruction<ForwardingRules> forwarding;
    private ProviderLabRouting router;
    private boolean addForwardingRecipient;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        // V1.0.21 creates this coordination table; it has no JPA entity for Hibernate's test DDL.
        tx.executeWithoutResult(status -> em.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate());
        forwarding = mockConstruction(ForwardingRules.class, (mock, context) -> {
            when(mock.getStatus(anyString(), anyString())).thenReturn("N");
            when(mock.getProviders(anyString(), anyString())).thenAnswer(call -> {
                var recipients = new ArrayList<ArrayList<String>>();
                String provider = call.getArgument(0);
                if (provider.equals("OLD-MRP")) recipients.add(new ArrayList<>(List.of("FORWARD")));
                if (provider.equals("FORWARD")) recipients.add(new ArrayList<>(List.of("OLD-MRP")));
                if (provider.equals("FORWARD") && addForwardingRecipient) {
                    recipients.add(new ArrayList<>(List.of("NEW-FORWARD")));
                }
                return recipients;
            });
        });
        router = new ProviderLabRouting();
    }

    @AfterEach
    void tearDown() {
        forwarding.close();
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from ProviderLabRoutingModel where labNo=:id").setParameter("id", REPORT).executeUpdate();
            em.createNativeQuery("delete from providerLabRoutingLock where lab_no=:id").setParameter("id", REPORT).executeUpdate();
        });
    }

    private List<String> providers() {
        return tx.execute(status -> routes.findAllLabRoutingByIdandType(REPORT, "HL7").stream()
                .map(ProviderLabRoutingModel::getProviderNo).sorted().toList());
    }

    @Test
    void shouldRemoveOldAutomaticAccessIncludingFiledRows_whenPatientIsCorrected() {
        router.routeMagic(REPORT, "ORDERING", "HL7");
        assertThat(router.reconcileMrpRouting(REPORT, "HL7", 101, "OLD-MRP")).isTrue();
        tx.executeWithoutResult(status -> {
            var row = routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", "OLD-MRP").get(0);
            row.setStatus("F");
            routes.merge(row);
        });
        assertThat(router.reconcileMrpRouting(REPORT, "HL7", 202, "NEW-MRP")).isTrue();
        assertThat(providers()).containsExactly("NEW-MRP", "ORDERING");
    }

    @Test
    void shouldKeepAcknowledgementAndAvoidDuplicateRows_whenSamePatientIsMatchedAgain() {
        router.reconcileMrpRouting(REPORT, "HL7", 101, "OLD-MRP");
        tx.executeWithoutResult(status -> {
            var row = routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", "OLD-MRP").get(0);
            row.setStatus("A");
            row.setComment("clinical acknowledgement");
            routes.merge(row);
        });
        assertThat(router.reconcileMrpRouting(REPORT, "HL7", 101, "OLD-MRP")).isFalse();
        assertThat(providers()).containsExactly("FORWARD", "OLD-MRP");
        tx.executeWithoutResult(status -> {
            var row = routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", "OLD-MRP").get(0);
            assertThat(row.getStatus()).isEqualTo("A");
            assertThat(row.getComment()).isEqualTo("clinical acknowledgement");
        });
    }

    @Test
    void shouldAddNewForwardingRecipient_whenAcknowledgedMrpIsMatchedAgain() {
        router.reconcileMrpRouting(REPORT, "HL7", 101, "OLD-MRP");
        tx.executeWithoutResult(status -> {
            var row = routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", "OLD-MRP").getFirst();
            row.setStatus("A");
            routes.merge(row);
        });
        addForwardingRecipient = true;

        assertThat(router.reconcileMrpRouting(REPORT, "HL7", 101, "OLD-MRP")).isFalse();
        assertThat(providers()).containsExactly("FORWARD", "NEW-FORWARD", "OLD-MRP");
        tx.executeWithoutResult(status -> {
            assertThat(routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", "OLD-MRP").getFirst().getStatus())
                    .isEqualTo("A");
            assertThat(routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", "NEW-FORWARD").getFirst().getMrpDemographicNo())
                    .isEqualTo(101);
        });
        router.reconcileMrpRouting(REPORT, "HL7", 202, "NEW-MRP");
        assertThat(providers()).containsExactly("NEW-MRP");
    }

    @Test
    void shouldTrackNewForwardingAsAutomatic_whenMrpAlreadyHasIndependentAccess() {
        router.routeMagic(REPORT, "OLD-MRP", "HL7");
        addForwardingRecipient = true;

        assertThat(router.reconcileMrpRouting(REPORT, "HL7", 101, "OLD-MRP")).isFalse();
        assertThat(providers()).containsExactly("FORWARD", "NEW-FORWARD", "OLD-MRP");
        router.reconcileMrpRouting(REPORT, "HL7", 202, "NEW-MRP");
        assertThat(providers()).containsExactly("FORWARD", "NEW-MRP", "OLD-MRP");
    }

    @Test
    void shouldKeepIndependentDeliveryAndForwarding_whenPatientIsCorrected() {
        router.reconcileMrpRouting(REPORT, "HL7", 101, "OLD-MRP");
        router.routeMagic(REPORT, "OLD-MRP", "HL7");
        router.reconcileMrpRouting(REPORT, "HL7", 202, "NEW-MRP");
        assertThat(providers()).containsExactly("FORWARD", "NEW-MRP", "OLD-MRP");
        tx.executeWithoutResult(status -> assertThat(routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", "OLD-MRP"))
                .allSatisfy(row -> assertThat(row.getMrpDemographicNo()).isNull()));
    }

    @Test
    void shouldRestoreUnassignedWithoutTouchingOtherTypes_whenRulesNoLongerAllowRouting() {
        router.routeMagic(REPORT, "DOCUMENT-OWNER", "DOC");
        router.reconcileMrpRouting(REPORT, "HL7", 101, "OLD-MRP");
        router.reconcileMrpRouting(REPORT, "HL7", 202, null);
        assertThat(providers()).containsExactly("0");
        tx.executeWithoutResult(status -> assertThat(routes.findAllLabRoutingByIdandType(REPORT, "DOC"))
                .extracting(ProviderLabRoutingModel::getProviderNo).containsExactly("DOCUMENT-OWNER"));
    }

    @Test
    void shouldRestoreOldAccess_whenTransactionFailsAfterReplacement() {
        router.reconcileMrpRouting(REPORT, "HL7", 101, "OLD-MRP");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            router.reconcileMrpRouting(REPORT, "HL7", 202, "NEW-MRP");
            em.flush();
            throw new IllegalStateException("injected failure after replacement");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(providers()).containsExactly("FORWARD", "OLD-MRP");
    }
}
