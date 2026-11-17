/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.upload;

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

/** Uses persisted rules and real routing to enforce report-source boundaries. */
@Tag("integration")
class LabForwardingTypesIntegrationTest extends CarlosTestBase {
    private static final int REPORT = 9834010;
    private static final String MRP = "983411";
    private static final String RECIPIENT = "983412";
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    @Autowired private ProviderLabRoutingDao routes;
    private IncomingLabRules rule;

    @BeforeEach
    void setUpForwarding() {
        em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
        Provider recipient = new Provider(RECIPIENT, "Synthetic", "doctor", "F", "", "Forwarding");
        recipient.setStatus("1");
        em.persist(recipient);
        rule = new IncomingLabRules();
        rule.setProviderNo(MRP);
        rule.setFrwdProviderNo(RECIPIENT);
        rule.setStatus("F");
    }

    private void saveRule(String type) {
        if (type != null) rule.addForwardType(type);
        em.persist(rule);
        em.flush();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HL7", "MDS", "CML", "BCP", "DOC"})
    void shouldNotForwardOrFileReport_whenRuleIsRestrictedToHrm(String source) {
        saveRule("HRM");
        new ProviderLabRouting().routeMagic(REPORT, MRP, source);
        assertThat(routes.findAllLabRoutingByIdandType(REPORT, source)).singleElement().satisfies(row -> {
            assertThat(row.getProviderNo()).isEqualTo(MRP);
            assertThat(row.getStatus()).isEqualTo("N");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"HL7", "MDS", "CML", "BCP"})
    void shouldForwardLegacyLabSources_whenRuleIncludesLabs(String source) {
        saveRule("HL7");
        new ProviderLabRouting().routeMagic(REPORT, MRP, source);
        assertThat(routes.findAllLabRoutingByIdandType(REPORT, source))
                .extracting(ProviderLabRoutingModel::getProviderNo).containsExactlyInAnyOrder(MRP, RECIPIENT);
    }

    @Test
    void shouldReactivateRemovedRoutingAndApplyRules_whenLabIsForwardedAgain() {
        saveRule("HL7");
        var removed = new ProviderLabRoutingModel(MRP, REPORT, "X", "previous comment", new Date(1000), "HL7");
        removed.setMrpDemographicNo(101);
        routes.persist(removed);
        em.flush();
        Integer originalId = removed.getId();

        new ProviderLabRouting().routeMagic(REPORT, MRP, "HL7");
        em.flush();
        em.clear();

        assertThat(routes.findAllLabRoutingByIdandType(REPORT, "HL7")).hasSize(2);
        assertThat(routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", MRP)).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(originalId);
            assertThat(row.getStatus()).isEqualTo("F");
            assertThat(row.getComment()).isEqualTo("previous comment");
            assertThat(row.getTimestamp().getTime()).isGreaterThan(1000L);
            assertThat(row.getMrpDemographicNo()).isNull();
        });
        assertThat(routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", RECIPIENT)).singleElement()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo("N"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "F", "N"})
    void shouldPreserveExistingRoutingAndSkipRules_whenLabIsForwardedAgain(String status) {
        saveRule("HL7");
        var existing = new ProviderLabRoutingModel(MRP, REPORT, status, "review comment", new Date(1000), "HL7");
        routes.persist(existing);
        em.flush();

        new ProviderLabRouting().routeMagic(REPORT, MRP, "HL7");
        em.flush();
        em.clear();

        assertThat(routes.findAllLabRoutingByIdandType(REPORT, "HL7")).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(existing.getId());
            assertThat(row.getStatus()).isEqualTo(status);
            assertThat(row.getComment()).isEqualTo("review comment");
            assertThat(row.getTimestamp().getTime()).isEqualTo(1000L);
        });
    }

    @Test
    void shouldKeepLegacyForwarding_whenRuleHasNoTypeMetadata() {
        saveRule(null);
        new ProviderLabRouting().routeMagic(REPORT, MRP, "HL7");
        assertThat(routes.findAllLabRoutingByIdandType(REPORT, "HL7"))
                .extracting(ProviderLabRoutingModel::getProviderNo).containsExactlyInAnyOrder(MRP, RECIPIENT);
    }

    @ParameterizedTest
    @CsvSource({"A,true", "A,false", "F,true", "F,false", "N,true", "N,false"})
    void shouldPreserveMixedDuplicateRouting_whenLabIsForwardedAgain(String status, boolean removedFirst) {
        saveRule("HL7");
        var removed = new ProviderLabRoutingModel(MRP, REPORT, "X", "removed comment", new Date(1000), "HL7");
        var active = new ProviderLabRoutingModel(MRP, REPORT, status, "review comment", new Date(2000), "HL7");
        routes.persist(removedFirst ? removed : active);
        routes.persist(removedFirst ? active : removed);
        em.flush();

        new ProviderLabRouting().routeMagic(REPORT, MRP, "HL7");
        em.flush();
        em.clear();

        assertThat(routes.findAllLabRoutingByIdandType(REPORT, "HL7")).hasSize(2).allSatisfy(row -> {
            assertThat(row.getProviderNo()).isEqualTo(MRP);
            if (row.getId().equals(removed.getId())) {
                assertThat(row.getStatus()).isEqualTo("X");
                assertThat(row.getComment()).isEqualTo("removed comment");
                assertThat(row.getTimestamp().getTime()).isEqualTo(1000L);
            } else {
                assertThat(row.getId()).isEqualTo(active.getId());
                assertThat(row.getStatus()).isEqualTo(status);
                assertThat(row.getComment()).isEqualTo("review comment");
                assertThat(row.getTimestamp().getTime()).isEqualTo(2000L);
            }
        });
    }

    @Test
    void shouldReactivateRemovedDuplicates_whenNoActiveRoutingExists() {
        saveRule("HL7");
        var first = new ProviderLabRoutingModel(MRP, REPORT, "X", "first removed", new Date(1000), "HL7");
        var second = new ProviderLabRoutingModel(MRP, REPORT, "X", "second removed", new Date(2000), "HL7");
        routes.persist(first);
        routes.persist(second);
        em.flush();

        new ProviderLabRouting().routeMagic(REPORT, MRP, "HL7");
        em.flush();
        em.clear();

        assertThat(routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", MRP))
                .hasSize(2).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo("F"))
                .extracting(ProviderLabRoutingModel::getId).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(routes.findByLabNoAndLabTypeAndProviderNo(REPORT, "HL7", RECIPIENT)).singleElement()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo("N"));
    }

    @Test
    void shouldRevokeAutomaticForwarding_whenRuleIsChangedToHrmOnly() {
        saveRule("HL7");
        ProviderLabRouting router = new ProviderLabRouting();
        router.reconcileMrpRouting(REPORT, "HL7", 101, MRP);
        assertThat(routes.findAllLabRoutingByIdandType(REPORT, "HL7"))
                .extracting(ProviderLabRoutingModel::getProviderNo).containsExactlyInAnyOrder(MRP, RECIPIENT);
        rule.getForwardTypes().getFirst().setType("HRM");
        em.flush();
        router.reconcileMrpRouting(REPORT, "HL7", 101, MRP);
        assertThat(routes.findAllLabRoutingByIdandType(REPORT, "HL7"))
                .extracting(ProviderLabRoutingModel::getProviderNo).containsExactly(MRP);
    }
}
