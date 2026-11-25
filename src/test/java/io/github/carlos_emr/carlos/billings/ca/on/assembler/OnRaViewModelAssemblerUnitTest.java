/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnRaService;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("billing")
class OnRaViewModelAssemblerUnitTest {
    @ParameterizedTest
    @CsvSource({"false,false,clinic", "true,false,site", "false,true,team", "true,true,team"})
    void shouldUseEffectiveSitePrivacyAndIndependentTeamBilling_forRemittanceHistory(
            boolean multisites, boolean teamBilling, String expected) {
        var security = mock(SecurityInfoManager.class);
        var reports = mock(BillingOnRaService.class);
        var login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("803601");
        when(security.hasPrivilege(login, "_site_access_privacy", "r", null)).thenReturn(true);
        when(security.hasPrivilege(login, "_team_billing_only", "r", null)).thenReturn(teamBilling);
        try (var settings = mockStatic(IsPropertiesOn.class)) {
            settings.when(IsPropertiesOn::isMultisitesEnable).thenReturn(multisites);
            new OnRaViewModelAssembler(security, reports).assemble(new MockHttpServletRequest(), login);
        }
        switch (expected) {
            case "clinic" -> verify(reports).getAllRahd("D");
            case "site" -> verify(reports).getSiteRahd("D", "803601");
            case "team" -> verify(reports).getTeamRahd("D", "803601");
            default -> throw new AssertionError(expected);
        }
        verifyNoMoreInteractions(reports);
    }
}
