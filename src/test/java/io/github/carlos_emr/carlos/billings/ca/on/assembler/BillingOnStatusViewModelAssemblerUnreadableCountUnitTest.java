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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnErrorReportService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingRaLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingStatusLoader;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnStatusViewModel;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins round-6 P1-1/P1-5: the unreadable-row count must propagate from
 * {@link BillingRaLookupService.AmountPaidResult#unreadableCount()} (and
 * from a NumberFormatException on {@code ch1.total}) through
 * {@link BillingOnStatusViewModelAssembler#assemble} into the view model
 * via {@code unreadableTotalRowCount}, so the JSP banners
 * "N rows excluded" instead of silently understating the grand total.
 */
@DisplayName("BillingOnStatusViewModelAssembler unreadable-count propagation")
@Tag("unit")
@Tag("billing")
class BillingOnStatusViewModelAssemblerUnreadableCountUnitTest {

    private SecurityInfoManager securityInfoManager;
    private BillingOnLookupService lookupService;
    private BillingStatusLoader statusPrep;
    private BillingOnErrorReportService errorRepImpl;
    private SiteDao siteDao;
    private BillingRaLookupService raLookupService;
    private LoggedInInfo loggedInInfo;
    private BillingOnStatusViewModelAssembler assembler;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        lookupService = mock(BillingOnLookupService.class);
        statusPrep = mock(BillingStatusLoader.class);
        errorRepImpl = mock(BillingOnErrorReportService.class);
        siteDao = mock(SiteDao.class);
        raLookupService = mock(BillingRaLookupService.class);
        loggedInInfo = mock(LoggedInInfo.class);

        when(lookupService.getCurProviderStr()).thenReturn(Collections.emptyList());
        when(lookupService.getCurTeamProviderStr(any())).thenReturn(Collections.emptyList());

        assembler = new BillingOnStatusViewModelAssembler(
                securityInfoManager, lookupService, statusPrep, errorRepImpl, siteDao, raLookupService);
    }

    private BillingClaimHeaderDto headerWithTotal(String total) {
        BillingClaimHeaderDto h = new BillingClaimHeaderDto();
        h.setId("123");
        h.setTotal(total);
        h.setBilling_date("2026-04-28");
        h.setProvider_ohip_no("PRV01");
        h.setPay_program("HCP");
        h.setTransc_id("9");
        return h;
    }

    private Map<String, ArrayList<HashMap<String, String>>> raBatch(BillingClaimHeaderDto header,
                                                                    ArrayList<HashMap<String, String>> rows) {
        return Map.of(BillingRaLookupService.RaDataRequest.key(
                header.getId(),
                header.getBilling_date().replaceAll("\\D", ""),
                header.getProvider_ohip_no()), rows);
    }

    @Test
    void shouldExposeUnreadableCount_whenAmountPaidResultReportsUnreadable() {
        // Two unreadable rows surfaced by raLookupService.getAmountPaidWithCount;
        // assembler must add r.unreadableCount() into unreadableTotalRowCount,
        // not silently drop the count.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("billType", "OHIP");

        BillingClaimHeaderDto header = headerWithTotal("100.00");
        when(statusPrep.getBills(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(header));

        ArrayList<HashMap<String, String>> raList = new ArrayList<>();
        raList.add(new HashMap<>());
        when(raLookupService.getRADataInternBatch(any()))
                .thenReturn(raBatch(header, raList));
        when(raLookupService.getAmountPaidWithCount(any(), anyString(), anyString()))
                .thenReturn(new BillingRaLookupService.AmountPaidResult("50.00", 2));
        when(raLookupService.getErrorCodes(any())).thenReturn("");

        BillingOnStatusViewModel vm = assembler.assemble(req, loggedInInfo);

        assertThat(vm.getUnreadableTotalRowCount())
                .as("AmountPaidResult.unreadableCount must propagate to view model")
                .isEqualTo(2);
        assertThat(vm.isPartialTotal()).isTrue();
    }

    @Test
    void shouldExposeUnreadableCount_whenChTotalIsUnparseable() {
        // ch1.total = "garbage" trips the NumberFormatException catch in
        // buildBillRows; that branch must bump unreadableTotalRowCount.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("billType", "OHIP");

        BillingClaimHeaderDto header = headerWithTotal("garbage");
        when(statusPrep.getBills(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(header));

        when(raLookupService.getRADataInternBatch(any()))
                .thenReturn(raBatch(header, new ArrayList<>()));

        BillingOnStatusViewModel vm = assembler.assemble(req, loggedInInfo);

        assertThat(vm.getUnreadableTotalRowCount())
                .as("Malformed ch1.total must bump unreadableTotalRowCount")
                .isEqualTo(1);
        assertThat(vm.isPartialTotal()).isTrue();
    }

    @Test
    void shouldExposeZeroUnreadableCount_whenAllRowsAreClean() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("billType", "OHIP");

        BillingClaimHeaderDto header = headerWithTotal("100.00");
        when(statusPrep.getBills(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(header));

        when(raLookupService.getRADataInternBatch(any()))
                .thenReturn(raBatch(header, new ArrayList<>()));

        BillingOnStatusViewModel vm = assembler.assemble(req, loggedInInfo);

        assertThat(vm.getUnreadableTotalRowCount()).isZero();
        assertThat(vm.isPartialTotal()).isFalse();
    }
}
