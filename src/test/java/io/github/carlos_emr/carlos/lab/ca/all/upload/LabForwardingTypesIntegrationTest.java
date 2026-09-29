/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.upload;

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
    void shouldKeepLegacyForwarding_whenRuleHasNoTypeMetadata() {
        saveRule(null);
        new ProviderLabRouting().routeMagic(REPORT, MRP, "HL7");
        assertThat(routes.findAllLabRoutingByIdandType(REPORT, "HL7"))
                .extracting(ProviderLabRoutingModel::getProviderNo).containsExactlyInAnyOrder(MRP, RECIPIENT);
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
