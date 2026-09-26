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
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.carlos_emr.carlos.commn.dao.IncomingLabRulesDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRulesType;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;
import io.github.carlos_emr.carlos.hospitalReportManager.service.HrmProviderRoutingService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Provider Linking Rules (issue #3971) against the H2 schema: the switch's property row and the
 * HRM routing the rules share with manual provider assignment.
 */
@Tag("integration")
@Tag("lab")
@DisplayName("Provider Linking Rules persistence")
class ProviderLinkingRulesIntegrationTest extends CarlosTestBase {

    @Autowired private ProviderLinkingRulesService rules;
    @Autowired private HrmProviderRoutingService hrmRouting;
    @Autowired private PropertyDao propertyDao;
    @Autowired private HRMDocumentDao documents;
    @Autowired private HRMDocumentToProviderDao routes;
    @Autowired private IncomingLabRulesDao incomingLabRulesDao;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;

    private MockedStatic<LogAction> logAction;

    @BeforeEach
    void setUp() {
        // LogAction writes asynchronously on its own thread; the audit call is pinned by the unit test.
        logAction = mockStatic(LogAction.class);
        em.createQuery("DELETE FROM Property p WHERE p.name = :name")
                .setParameter("name", "provider_linking_rules").executeUpdate();
    }

    @AfterEach
    void tearDown() {
        logAction.close();
    }

    @Test
    @DisplayName("should store a global row on first save and read it back")
    void shouldPersistGlobalRow_whenSwitchIsSaved() {
        assertThat(rules.isEnabled()).isFalse();

        rules.setEnabled(mock(LoggedInInfo.class), true);
        em.flush();
        em.clear();

        List<Property> stored = propertyDao.findGlobalByName(Property.PROPERTY_KEY.provider_linking_rules);
        assertThat(stored).singleElement().satisfies(row -> assertThat(row.getValue()).isEqualTo("true"));
        assertThat(rules.isEnabled()).isTrue();

        rules.setEnabled(mock(LoggedInInfo.class), false);
        em.flush();
        em.clear();
        assertThat(propertyDao.findGlobalByName(Property.PROPERTY_KEY.provider_linking_rules)).hasSize(1);
        assertThat(rules.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should honour an SQL-seeded row with an empty provider but not a provider's own row")
    void shouldReadOnlyClinicRows_whenRowsWereSeededBySql() {
        Property providerRow = new Property();
        providerRow.setName("provider_linking_rules");
        providerRow.setProviderNo("999998");
        providerRow.setValue("true");
        propertyDao.persist(providerRow);
        em.flush();
        assertThat(rules.isEnabled()).isFalse();

        Property sqlSeeded = new Property();
        sqlSeeded.setName("provider_linking_rules");
        sqlSeeded.setProviderNo("");
        sqlSeeded.setValue("true");
        propertyDao.persist(sqlSeeded);
        em.flush();
        assertThat(rules.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("should route an HRM report, forward it and clear the unclaimed row in one pass")
    void shouldRouteForwardAndClaim_whenHrmReportIsAssigned() {
        HRMDocument document = new HRMDocument();
        document.setDescription("Provider linking rules integration fixture");
        documents.persist(document);
        int reportId = document.getId();
        HRMDocumentToProvider unclaimed = new HRMDocumentToProvider();
        unclaimed.setHrmDocumentId(reportId);
        unclaimed.setProviderNo("-1");
        unclaimed.setSignedOff(0);
        routes.persist(unclaimed);

        IncomingLabRules rule = new IncomingLabRules();
        rule.setProviderNo("101");
        rule.setFrwdProviderNo("202");
        rule.setStatus("N");
        IncomingLabRulesType hrm = new IncomingLabRulesType();
        hrm.setType("HRM");
        ArrayList<IncomingLabRulesType> types = new ArrayList<>(List.of(hrm));
        rule.setForwardTypes(types);
        incomingLabRulesDao.persist(rule);
        em.flush();

        assertThat(hrmRouting.assignProvider(reportId, "101")).isTrue();
        assertThat(hrmRouting.assignProvider(reportId, "101")).isFalse();
        em.flush();
        em.clear();

        assertThat(routes.findByHrmDocumentId(reportId))
                .extracting(HRMDocumentToProvider::getProviderNo)
                .containsExactlyInAnyOrder("101", "202");
    }
}
