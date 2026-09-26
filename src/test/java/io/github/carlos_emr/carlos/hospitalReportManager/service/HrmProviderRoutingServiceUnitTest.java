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
package io.github.carlos_emr.carlos.hospitalReportManager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.IncomingLabRulesDao;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRulesType;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;

@Tag("unit")
@Tag("hrm")
@DisplayName("HrmProviderRoutingService")
class HrmProviderRoutingServiceUnitTest {

    private static final int REPORT = 7;

    private HRMDocumentToProviderDao routingDao;
    private IncomingLabRulesDao rulesDao;
    private HrmProviderRoutingService service;

    @BeforeEach
    void setUp() {
        routingDao = mock(HRMDocumentToProviderDao.class);
        rulesDao = mock(IncomingLabRulesDao.class);
        service = new HrmProviderRoutingService(routingDao, rulesDao);
    }

    private static HRMDocumentToProvider routing(String providerNo) {
        HRMDocumentToProvider row = new HRMDocumentToProvider();
        row.setHrmDocumentId(REPORT);
        row.setProviderNo(providerNo);
        row.setSignedOff(0);
        return row;
    }

    private static IncomingLabRules rule(String forwardTo, String... types) {
        IncomingLabRules rule = new IncomingLabRules();
        rule.setFrwdProviderNo(forwardTo);
        ArrayList<IncomingLabRulesType> forwardTypes = new ArrayList<>();
        for (String type : types) {
            IncomingLabRulesType forwardType = new IncomingLabRulesType();
            forwardType.setType(type);
            forwardTypes.add(forwardType);
        }
        rule.setForwardTypes(forwardTypes);
        return rule;
    }

    @Test
    @DisplayName("should add an unsigned routing row for a provider not yet routed")
    void shouldAddUnsignedRoutingRow_whenProviderIsNotRouted() {
        assertThat(service.assignProvider(REPORT, "101")).isTrue();

        verify(routingDao).persist(argThat((HRMDocumentToProvider row) -> row.getHrmDocumentId() == REPORT
                && "101".equals(row.getProviderNo()) && row.getSignedOff() == 0));
    }

    @Test
    @DisplayName("should not add a second row for a provider already routed")
    void shouldNotDuplicateRouting_whenProviderIsAlreadyRouted() {
        when(routingDao.findByHrmDocumentIdAndProviderNoList(REPORT, "101")).thenReturn(List.of(routing("101")));

        assertThat(service.assignProvider(REPORT, "101")).isFalse();

        verify(routingDao, never()).persist(any());
        verify(routingDao, never()).merge(any());
    }

    @Test
    @DisplayName("should apply only the provider's HRM forwarding rules, once each")
    void shouldApplyHrmForwardingRules_forEachForwardProviderOnce() {
        when(rulesDao.findCurrentByProviderNo("101")).thenReturn(List.of(
                rule("202", "HL7", "HRM"),
                rule("303", "HL7", "DOC"),
                rule("404", "HRM")));
        when(routingDao.findByHrmDocumentIdAndProviderNoList(REPORT, "404")).thenReturn(List.of(routing("404")));

        service.assignProvider(REPORT, "101");

        verify(routingDao).persist(argThat((HRMDocumentToProvider row) -> "101".equals(row.getProviderNo())));
        verify(routingDao).persist(argThat((HRMDocumentToProvider row) -> "202".equals(row.getProviderNo())));
        verify(routingDao, never()).persist(argThat((HRMDocumentToProvider row) -> "303".equals(row.getProviderNo())));
        verify(routingDao, never()).persist(argThat((HRMDocumentToProvider row) -> "404".equals(row.getProviderNo())));
        // One hop only: the forwarded provider's own rules are not followed.
        verify(rulesDao, never()).findCurrentByProviderNo("202");
    }

    // AbstractModel.equals compares ids and these unsaved rows have none, so rows are matched by
    // identity with same().
    @Test
    @DisplayName("should remove every unclaimed (-1) row once a provider holds the report")
    void shouldRemoveEveryUnclaimedRow_whenProviderIsAssigned() {
        HRMDocumentToProvider unclaimed = routing("-1");
        HRMDocumentToProvider unclaimedDuplicate = routing("-1");
        when(routingDao.findByHrmDocumentIdAndProviderNoList(REPORT, "-1"))
                .thenReturn(List.of(unclaimed, unclaimedDuplicate));

        service.assignProvider(REPORT, "101");

        verify(routingDao).remove(same(unclaimed));
        verify(routingDao).remove(same(unclaimedDuplicate));
        verify(routingDao, times(2)).remove(any(HRMDocumentToProvider.class));
    }

    @Test
    @DisplayName("should tolerate a DAO that answers null lists")
    void shouldTolerateNullLists_fromLegacyDao() {
        when(routingDao.findByHrmDocumentIdAndProviderNoList(anyInt(), anyString())).thenReturn(null);
        when(rulesDao.findCurrentByProviderNo("101")).thenReturn(null);

        assertThat(service.assignProvider(REPORT, "101")).isTrue();

        verify(routingDao).persist(any(HRMDocumentToProvider.class));
        verify(routingDao, never()).remove(any(HRMDocumentToProvider.class));
        verify(routingDao, never()).findByHrmDocumentIdAndProviderNo(eq(REPORT), anyString());
    }
}
