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
package io.github.carlos_emr.carlos.lab.ca.all.upload;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.lab.service.MrpRoutingService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * HL7 upload routing with Provider Linking Rules on and off (issue #3971).
 */
@Tag("unit")
@Tag("lab")
@DisplayName("MessageUploader provider routing")
class MessageUploaderProviderRoutingUnitTest extends CarlosUnitTestBase {

    private ProviderLabRouting routing;
    private MrpRoutingService mrpRouting;

    @BeforeEach
    void setUp() {
        // MessageUploader resolves these in its static initializer.
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(RecycleBinDao.class);
        createAndRegisterMock(Hl7TextInfoDao.class);
        createAndRegisterMock(Hl7TextMessageDao.class);
        createAndRegisterMock(MeasurementsExtDao.class);
        createAndRegisterMock(MeasurementDao.class);
        createAndRegisterMock(FileUploadCheckDao.class);
        createAndRegisterMock(DemographicManager.class);
        routing = mock(ProviderLabRouting.class);
        mrpRouting = mock(MrpRoutingService.class);
    }

    private static Set<String> providers(String... providerNos) {
        return new LinkedHashSet<>(List.of(providerNos));
    }

    @Test
    @DisplayName("should route only to the ordering providers when the rules are off")
    void shouldRouteOnlyToOrderingProviders_whenRulesAreOff() throws Exception {
        MessageUploader.routeToProviders("555", providers("201", "202"), "101", routing, mrpRouting, "999998");

        InOrder order = inOrder(routing);
        order.verify(routing).route("555", "201", "HL7");
        order.verify(routing).route("555", "202", "HL7");
        verifyNoMoreInteractions(routing);
        verify(mrpRouting, never()).recordUploadRouting(any(), any(), any());
    }

    @Test
    @DisplayName("should also route to the MRP and audit it when the rules are on")
    void shouldAlsoRouteToMrp_whenRulesAreOn() throws Exception {
        when(mrpRouting.shouldRouteUploadToMrp("101")).thenReturn(true);

        MessageUploader.routeToProviders("555", providers("201"), "101", routing, mrpRouting, "999998");

        verify(routing).route("555", "201", "HL7");
        verify(routing).route("555", "101", "HL7");
        verify(mrpRouting).recordUploadRouting("555", "101", "999998");
    }

    @Test
    @DisplayName("should not route or audit twice when the MRP ordered the test")
    void shouldNotRouteTwice_whenMrpIsOrderingProvider() throws Exception {
        when(mrpRouting.shouldRouteUploadToMrp("101")).thenReturn(true);

        MessageUploader.routeToProviders("555", providers("101", "201"), "101", routing, mrpRouting, null);

        verify(routing).route("555", "101", "HL7");
        verify(routing).route("555", "201", "HL7");
        verifyNoMoreInteractions(routing);
        verify(mrpRouting, never()).recordUploadRouting(any(), any(), any());
    }

    @Test
    @DisplayName("should skip the MRP when the rules decline it")
    void shouldSkipMrp_whenRulesDeclineIt() throws Exception {
        when(mrpRouting.shouldRouteUploadToMrp("0")).thenReturn(false);

        MessageUploader.routeToProviders("555", providers("201"), "0", routing, mrpRouting, null);

        verify(routing).route("555", "201", "HL7");
        verifyNoMoreInteractions(routing);
    }

    @Test
    @DisplayName("should keep the unmatched fallback to the MRP whatever the rules say")
    void shouldFallBackToMrp_whenNoProviderMatched() throws Exception {
        MessageUploader.routeToProviders("555", providers(), "101", routing, mrpRouting, null);

        verify(routing).route("555", "101", "HL7");
        verifyNoMoreInteractions(routing);
        verify(mrpRouting, never()).shouldRouteUploadToMrp(anyString());
    }

    @Test
    @DisplayName("should keep the unassigned inbox when neither a provider nor a patient matched")
    void shouldRouteToUnassigned_whenNothingMatched() throws Exception {
        MessageUploader.routeToProviders("555", providers(), "0", routing, mrpRouting, null);

        verify(routing).route("555", "0", "HL7");
        verifyNoMoreInteractions(routing);
    }
}
