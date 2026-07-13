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

import java.util.ArrayList;
import java.util.Properties;

import io.github.carlos_emr.carlos.billings.ca.on.service.GstReportService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GstReportViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code GstReportViewModelAssembler} GST report presentation rows. */
@DisplayName("GstReportViewModelAssembler")
@Tag("unit")
@Tag("billing")
class GstReportViewModelAssemblerUnitTest {

    @Test
    void shouldTreatNullProviderLookup_asEmptyList() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        GstReportService gstReport = mock(GstReportService.class);
        BillingOnLookupService lookupService = mock(BillingOnLookupService.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), any(String.class), eq("r"), isNull()))
                .thenReturn(false);
        when(lookupService.getCurProviderStr()).thenReturn(null);
        when(gstReport.getGST(eq(loggedInInfo), eq(""), eq(""), eq("")))
                .thenReturn(new ArrayList<>());
        GstReportViewModelAssembler assembler =
                new GstReportViewModelAssembler(securityInfoManager, gstReport, lookupService);

        GstReportViewModel model = assembler.assemble(new MockHttpServletRequest(), loggedInInfo);

        assertThat(model.getProviderOptions()).isEmpty();
        assertThat(model.getRows()).isEmpty();
    }

    @Test
    void shouldThrowDataLoadException_whenGstAmountIsMalformed() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        GstReportService gstReport = mock(GstReportService.class);
        BillingOnLookupService lookupService = mock(BillingOnLookupService.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), any(String.class), eq("r"), isNull()))
                .thenReturn(false);
        when(lookupService.getCurProviderStr()).thenReturn(null);
        Properties row = new Properties();
        row.setProperty("total", "100.00");
        row.setProperty("gst", "not-money");
        when(gstReport.getGST(eq(loggedInInfo), eq(""), eq(""), eq("")))
                .thenReturn(new ArrayList<>(java.util.List.of(row)));
        GstReportViewModelAssembler assembler =
                new GstReportViewModelAssembler(securityInfoManager, gstReport, lookupService);

        assertThatThrownBy(() -> assembler.assemble(new MockHttpServletRequest(), loggedInInfo))
                .isInstanceOf(BillingDataLoadException.class)
                .hasMessageContaining("GST report amount");
    }
}
