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

import io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewLoader;
import io.github.carlos_emr.carlos.billings.ca.on.service.OhipClaimFileService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOhipSimulationViewModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingOhipSimulationViewModelAssembler} preview-row assembly. */
@DisplayName("BillingOhipSimulationViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingOhipSimulationViewModelAssemblerUnitTest {

    @Test
    void shouldEncodeProviderErrorLines_beforeAppendingToLegacyPreviewRows() {
        String html = BillingOhipSimulationViewModelAssembler.formatErrorLine(
                "The billing code (<script>alert(1)</script>) for providers (999998) is not correct!");

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).endsWith("<br>");
    }

    @Test
    void shouldRenderProviderDropdown_fromTypedProviderEntries() {
        BillingReviewLoader reviewLoader = mock(BillingReviewLoader.class);
        BillingOnLookupService lookupService = mock(BillingOnLookupService.class);
        @SuppressWarnings("unchecked")
        ObjectFactory<OhipClaimFileService> ohipClaimFileFactory = mock(ObjectFactory.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(reviewLoader.getProviderBillingStr()).thenReturn(List.of(
                new ProviderDropdownEntry("999998", "Last", "First", "123456", "1234", "01")));

        BillingOhipSimulationViewModel model = new BillingOhipSimulationViewModelAssembler(
                reviewLoader, lookupService, ohipClaimFileFactory)
                .assemble(new MockHttpServletRequest(), loggedInInfo, false, false, false);

        assertThat(model.getProviders()).containsExactly(
                new BillingOhipSimulationViewModel.ProviderOption("999998", "Last", "First"));
    }

    @Test
    void shouldSubmitProviderSimulation_usingTypedProviderEntries() {
        BillingReviewLoader reviewLoader = mock(BillingReviewLoader.class);
        BillingOnLookupService lookupService = mock(BillingOnLookupService.class);
        OhipClaimFileService claimFileService = mock(OhipClaimFileService.class);
        @SuppressWarnings("unchecked")
        ObjectFactory<OhipClaimFileService> ohipClaimFileFactory = mock(ObjectFactory.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("submit", "Create Report");
        request.setParameter("providers", "all");
        request.setParameter("xml_appointment_date", "2026-05-01");

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(reviewLoader.getProviderBillingStr()).thenReturn(List.of(
                new ProviderDropdownEntry("999998", "Last", "First", "123456", "1234", "01")));
        when(lookupService.getProviderObj("999998")).thenReturn(provider("999998"));
        when(ohipClaimFileFactory.getObject()).thenReturn(claimFileService);
        when(claimFileService.getRecordCount()).thenReturn(0);
        when(claimFileService.getErrorMsg()).thenReturn("");
        when(claimFileService.getErrorFatalMsg()).thenReturn("");
        when(claimFileService.getBigTotal()).thenReturn(BigDecimal.ZERO);

        new BillingOhipSimulationViewModelAssembler(reviewLoader, lookupService, ohipClaimFileFactory)
                .assemble(request, loggedInInfo, false, false, false);

        verify(claimFileService).setProviderNo("999998");
    }

    private static BillingProviderDto provider(String providerNo) {
        BillingProviderDto dto = new BillingProviderDto();
        dto.setProviderNo(providerNo);
        dto.setOhipNo("123456");
        dto.setBillingGroupNo("1234");
        dto.setSpecialtyCode("01");
        return dto;
    }
}
