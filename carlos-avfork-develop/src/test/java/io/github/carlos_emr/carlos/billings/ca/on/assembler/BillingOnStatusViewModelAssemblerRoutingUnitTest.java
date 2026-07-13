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
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the simpler-vs-sorted query routing in
 * {@link BillingOnStatusViewModelAssembler}.
 *
 * <p>Before this test the {@code if ((serviceCode == null || billingForm == null)
 * && dx.length() < 2 && visitType.length() < 2)} branch was dead because
 * {@code serviceCode} and {@code billingForm} were both normalized to sentinel
 * values ({@code "%"} / {@code "---"}) one block above. The legacy scriptlet's
 * intent — route ad-hoc / URL-navigated calls without filter params to the
 * cheaper {@link BillingStatusLoader#getBills} query — was lost.
 *
 * <p>The fix captures the original null/empty state of the filter params
 * before normalization (booleans {@code serviceCodeFilterAbsent} and
 * {@code billingFormFilterAbsent}) and routes on those flags, preserving the
 * legacy OR semantics.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingOnStatusViewModelAssembler routing")
@Tag("unit")
@Tag("billing")
class BillingOnStatusViewModelAssemblerRoutingUnitTest {

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

        when(statusPrep.getBills(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.<BillingClaimHeaderDto>emptyList());
        when(statusPrep.getBillsWithSorting(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.<BillingClaimHeaderDto>emptyList());

        assembler = new BillingOnStatusViewModelAssembler(
                securityInfoManager, lookupService, statusPrep, errorRepImpl, siteDao, raLookupService);
    }

    private MockHttpServletRequest baseRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        // Marks `search=true` so the assembler actually queries the DAO.
        req.setParameter("billType", "OHIP");
        return req;
    }

    @Test
    void shouldRouteToSimpleGetBills_whenServiceCodeAndBillingFormFiltersAbsent() {
        MockHttpServletRequest req = baseRequest();
        // serviceCode + billing_form intentionally not set — the assembler must
        // observe their absence (before defaulting) and pick the simpler path.

        assembler.assemble(req, loggedInInfo);

        verify(statusPrep, atLeastOnce())
                .getBills(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(statusPrep, never()).getBillsWithSorting(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRouteToGetBillsWithSorting_whenBothFiltersPresent() {
        MockHttpServletRequest req = baseRequest();
        req.setParameter("serviceCode", "A001");
        req.setParameter("billing_form", "X1");

        assembler.assemble(req, loggedInInfo);

        verify(statusPrep, never())
                .getBills(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(statusPrep, times(1)).getBillsWithSorting(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRouteToSimpleGetBills_whenOnlyServiceCodeFilterAbsent() {
        // Legacy OR-semantics: missing either filter triggers the simpler path.
        MockHttpServletRequest req = baseRequest();
        req.setParameter("billing_form", "X1");

        assembler.assemble(req, loggedInInfo);

        verify(statusPrep, atLeastOnce())
                .getBills(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(statusPrep, never()).getBillsWithSorting(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRouteToGetBillsWithSorting_whenDxIsLongEnough() {
        // dx >= 2 chars must always route to the sorted path, even with no
        // serviceCode/billing_form filters.
        MockHttpServletRequest req = baseRequest();
        req.setParameter("dx", "789");

        assembler.assemble(req, loggedInInfo);

        verify(statusPrep, never())
                .getBills(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(statusPrep, times(1)).getBillsWithSorting(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
    }
}
