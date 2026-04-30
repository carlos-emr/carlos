/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingThirdPartyRecordService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnCorrectionViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONErrorCodeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingOnCorrectionRenderComposer}. Exercise the
 * no-bill branch of {@code compose(...)}, which drives clinic-location loading,
 * the privilege-gated edit flag, and the first-party paid-block default — the
 * branches with no dependency on {@link io.github.carlos_emr.CarlosProperties}
 * state beyond defaultable scalar lookups.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingOnCorrectionRenderComposer")
@Tag("unit")
@Tag("billing")
class BillingOnCorrectionRenderComposerUnitTest {

    private SecurityInfoManager securityInfoManager;
    private BillingServiceDao billingServiceDao;
    private BillingONExtDao bExtDao;
    private BillingONPaymentDao billingONPaymentDao;
    private BillingONEAReportDao billingONEAReportDao;
    private BillingONErrorCodeDao billingONErrorCodeDao;
    private RaDetailDao raDetailDao;
    private ClinicLocationDao clinicLocationDao;
    private ClinicNbrDao clinicNbrDao;
    private BillingThirdPartyRecordService thirdPartyRecordService;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;

    private BillingOnCorrectionRenderComposer composer;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        billingServiceDao = mock(BillingServiceDao.class);
        bExtDao = mock(BillingONExtDao.class);
        billingONPaymentDao = mock(BillingONPaymentDao.class);
        billingONEAReportDao = mock(BillingONEAReportDao.class);
        billingONErrorCodeDao = mock(BillingONErrorCodeDao.class);
        raDetailDao = mock(RaDetailDao.class);
        clinicLocationDao = mock(ClinicLocationDao.class);
        clinicNbrDao = mock(ClinicNbrDao.class);
        thirdPartyRecordService = mock(BillingThirdPartyRecordService.class);
        loggedInInfo = mock(LoggedInInfo.class);

        request = new MockHttpServletRequest();

        composer = new BillingOnCorrectionRenderComposer(
                securityInfoManager, billingServiceDao, bExtDao, billingONPaymentDao,
                billingONEAReportDao, billingONErrorCodeDao, raDetailDao,
                clinicLocationDao, clinicNbrDao, thirdPartyRecordService);
    }

    @Test
    void shouldInstantiate_withAllRequiredDependencies() {
        assertThat(composer).isNotNull();
    }

    @Test
    void shouldRenderFirstPartyDefaults_whenBCh1IsNull() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(clinicLocationDao.findByClinicNo(1)).thenReturn(List.<ClinicLocation>of());

        BillingOnCorrectionViewModel.Builder b = BillingOnCorrectionViewModel.builder();
        composer.compose(b, request, loggedInInfo,
                /*bCh1*/ null,
                /*billNo*/ null,
                /*multiSiteProvider*/ false,
                /*payProgram*/ null);
        BillingOnCorrectionViewModel vm = b.build();

        // Privilege gate flips canEditBilling.
        assertThat(vm.isCanEditBilling()).isTrue();
        // No bill loaded → can never be a third-party invoice.
        assertThat(vm.isThirdParty()).isFalse();
        // First-party default block: matches the legacy inline scriptlet output prefix.
        assertThat(vm.getHtmlPaid()).startsWith("Paid<br>");
        assertThat(vm.getPayer()).isEmpty();
        // No bill → no error-report DAO calls and an empty entries list.
        assertThat(vm.getErrorReportEntries()).isEmpty();
        verifyNoInteractions(billingONEAReportDao, billingONErrorCodeDao, raDetailDao);
        // Clinic-location dropdown is loaded regardless of bill state.
        verify(clinicLocationDao).findByClinicNo(1);
    }

    @Test
    void shouldDenyEdit_whenPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);
        when(clinicLocationDao.findByClinicNo(1)).thenReturn(List.<ClinicLocation>of());

        BillingOnCorrectionViewModel.Builder b = BillingOnCorrectionViewModel.builder();
        composer.compose(b, request, loggedInInfo, null, null, false, null);
        BillingOnCorrectionViewModel vm = b.build();

        assertThat(vm.isCanEditBilling()).isFalse();
    }

    @Test
    void shouldDenyEdit_whenLoggedInInfoIsNull() {
        when(clinicLocationDao.findByClinicNo(1)).thenReturn(List.<ClinicLocation>of());

        BillingOnCorrectionViewModel.Builder b = BillingOnCorrectionViewModel.builder();
        composer.compose(b, request, /*loggedInInfo*/ null, null, null, false, null);
        BillingOnCorrectionViewModel vm = b.build();

        // Null session must short-circuit the privilege check rather than
        // reaching SecurityInfoManager (which would NPE on null principal).
        assertThat(vm.isCanEditBilling()).isFalse();
        verifyNoInteractions(securityInfoManager);
    }
}
