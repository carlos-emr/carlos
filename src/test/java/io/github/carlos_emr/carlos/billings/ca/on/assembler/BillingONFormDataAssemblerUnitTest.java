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

import java.util.Collections;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFormViewModel;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFavouriteDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.billing.CA.filters.CodeFilterManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Smoke unit tests for {@link BillingONFormDataAssembler}.
 *
 * <p>Plan A9 originally called for an integration test using
 * {@code CarlosTestBase} + H2; this is the unit-mock variant covering the
 * assembler's no-demographic / empty-data happy path. It does not exercise
 * the full service-code-grid prep (that needs a real DB) but locks down that
 * the assembler instantiates without NPE when all DAOs return empty data,
 * which is a meaningful regression check given the SpringUtils-singleton
 * coupling.</p>
 *
 * @since 2026-04-24
 */
@DisplayName("BillingONFormDataAssembler")
@Tag("unit")
@Tag("billing")
class BillingONFormDataAssemblerUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest request;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        DemographicManager demographicManager = Mockito.mock(DemographicManager.class);
        ProfessionalSpecialistDao professionalSpecialistDao = Mockito.mock(ProfessionalSpecialistDao.class);
        DxresearchDAO dxresearchDAO = Mockito.mock(DxresearchDAO.class);
        UserPropertyDAO userPropertyDAO = Mockito.mock(UserPropertyDAO.class);
        ProviderDao providerDao = Mockito.mock(ProviderDao.class);
        CtlBillingServiceDao ctlBillingServiceDao = Mockito.mock(CtlBillingServiceDao.class);
        ProviderPreferenceDao providerPreferenceDao = Mockito.mock(ProviderPreferenceDao.class);
        MyGroupDao myGroupDao = Mockito.mock(MyGroupDao.class);
        BillingServiceDao billingServiceDao = Mockito.mock(BillingServiceDao.class);
        CtlBillingServicePremiumDao ctlBillingServicePremiumDao = Mockito.mock(CtlBillingServicePremiumDao.class);
        CSSStylesDAO cssStylesDAO = Mockito.mock(CSSStylesDAO.class);
        CodeFilterManager codeFilterManager = Mockito.mock(CodeFilterManager.class);
        CtlBillingTypeDao ctlBillingTypeDao = Mockito.mock(CtlBillingTypeDao.class);
        DiagnosticCodeDao diagnosticCodeDao = Mockito.mock(DiagnosticCodeDao.class);

        // JdbcBillingPageUtil's constructor pulls a slew of additional DAOs.
        OscarAppointmentDao oscarAppointmentDao = Mockito.mock(OscarAppointmentDao.class);
        ClinicLocationDao clinicLocationDao = Mockito.mock(ClinicLocationDao.class);
        BillingPaymentTypeDao billingPaymentTypeDao = Mockito.mock(BillingPaymentTypeDao.class);
        BillingONFavouriteDao billingONFavouriteDao = Mockito.mock(BillingONFavouriteDao.class);
        BillingONFilenameDao billingONFilenameDao = Mockito.mock(BillingONFilenameDao.class);
        ProviderSiteDao providerSiteDao = Mockito.mock(ProviderSiteDao.class);

        // Manual-billing fallback now resolves providerNo to userNo when
        // apptProvider_no/xml_provider/providerview are all empty, which
        // triggers ProviderPreferencesUIBean's static init (loads these DAOs
        // via SpringUtils). Register stubs so the class loads.
        EFormDao eFormDao = Mockito.mock(EFormDao.class);
        EncounterFormDao encounterFormDao = Mockito.mock(EncounterFormDao.class);

        // Round-15: SiteDao + ClinicNbrDao added so the new
        // BillingONFormSiteContextComposer (multisite + RMA / clinic-nbr
        // pre-load) can resolve through SpringUtils. The composer itself
        // gates on IsPropertiesOn.isMultisitesEnable / rma_enabled, so
        // empty stubs are sufficient for unit tests.
        io.github.carlos_emr.carlos.commn.dao.SiteDao siteDao =
                Mockito.mock(io.github.carlos_emr.carlos.commn.dao.SiteDao.class);
        io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao clinicNbrDao =
                Mockito.mock(io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao.class);

        registerMock(DemographicManager.class, demographicManager);
        registerMock(ProfessionalSpecialistDao.class, professionalSpecialistDao);
        registerMock(DxresearchDAO.class, dxresearchDAO);
        registerMock(UserPropertyDAO.class, userPropertyDAO);
        registerMock(ProviderDao.class, providerDao);
        registerMock(CtlBillingServiceDao.class, ctlBillingServiceDao);
        registerMock(ProviderPreferenceDao.class, providerPreferenceDao);
        registerMock(MyGroupDao.class, myGroupDao);
        registerMock(BillingServiceDao.class, billingServiceDao);
        registerMock(CtlBillingServicePremiumDao.class, ctlBillingServicePremiumDao);
        registerMock(CSSStylesDAO.class, cssStylesDAO);
        registerMock(CodeFilterManager.class, codeFilterManager);
        registerMock(CtlBillingTypeDao.class, ctlBillingTypeDao);
        registerMock(DiagnosticCodeDao.class, diagnosticCodeDao);
        registerMock(OscarAppointmentDao.class, oscarAppointmentDao);
        registerMock(ClinicLocationDao.class, clinicLocationDao);
        registerMock(BillingPaymentTypeDao.class, billingPaymentTypeDao);
        registerMock(BillingONFavouriteDao.class, billingONFavouriteDao);
        registerMock(BillingONFilenameDao.class, billingONFilenameDao);
        registerMock(ProviderSiteDao.class, providerSiteDao);
        registerMock(EFormDao.class, eFormDao);
        registerMock(EncounterFormDao.class, encounterFormDao);
        registerMock(io.github.carlos_emr.carlos.commn.dao.SiteDao.class, siteDao);
        registerMock(io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao.class, clinicNbrDao);

        // Default empty returns so the assembler doesn't NPE on any path.
        when(demographicManager.getDemographic(any(), anyString())).thenReturn(null);
        when(providerDao.getProvidersWithNonEmptyCredentials()).thenReturn(Collections.emptyList());
        when(ctlBillingServiceDao.findServiceTypesByStatus(anyString())).thenReturn(Collections.emptyList());
        when(ctlBillingServiceDao.findByServiceTypeId(anyString())).thenReturn(Collections.emptyList());
        when(ctlBillingTypeDao.findByServiceType(anyString())).thenReturn(Collections.emptyList());
        when(userPropertyDAO.getProp(anyString())).thenReturn(null);

        request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("user", "999998");
        loggedInInfo = Mockito.mock(LoggedInInfo.class);
    }

    @Test
    void shouldReturnNonNullModel_whenAssembledWithMinimalContext() {
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        BillingONFormDataAssembler assembler = new BillingONFormDataAssembler();
        BillingONFormViewModel model = assembler.assemble(loggedInInfo, request);

        assertThat(model).isNotNull();
        assertThat(model.getUserNo()).isEqualTo("999998");
        assertThat(model.getDemographicNo()).isEqualTo("1");
        assertThat(model.getCtlBillForm()).isEqualTo("GP");
    }

    @Test
    void shouldDefaultDemoHcType_toON_whenDemographicMissing() {
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        BillingONFormDataAssembler assembler = new BillingONFormDataAssembler();
        BillingONFormViewModel model = assembler.assemble(loggedInInfo, request);

        assertThat(model.getDemoHcType()).isEqualTo("ON");
    }

    @Test
    void shouldFlagInvalidDob_whenDemographicMissing() {
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        BillingONFormDataAssembler assembler = new BillingONFormDataAssembler();
        BillingONFormViewModel model = assembler.assemble(loggedInInfo, request);

        assertThat(model.getErrorFlag()).isEqualTo("1");
        assertThat(model.getErrorMsg()).contains("does not have a valid DOB");
    }

    @Test
    void shouldExposeProviderViewFromXmlProviderParam_whenSupplied() {
        request.setParameter("xml_provider", "111111|something");
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        BillingONFormDataAssembler assembler = new BillingONFormDataAssembler();
        BillingONFormViewModel model = assembler.assemble(loggedInInfo, request);

        // The pipe is stripped — JSP's behaviour preserved.
        assertThat(model.getProviderView()).isEqualTo("111111");
    }

    @Test
    void shouldFallBackProviderNoToUserNo_whenApptProviderAndPickerAreBlank() {
        // Manual-billing path: all provider hints absent, so providerNo falls
        // back to the logged-in userNo. The earlier code returned "" here,
        // which broke provider-preference + default-billing-form lookups for
        // every manual bill creation.
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        BillingONFormDataAssembler assembler = new BillingONFormDataAssembler();
        BillingONFormViewModel model = assembler.assemble(loggedInInfo, request);

        assertThat(model.getProviderNo()).isEqualTo("999998");
    }

    @Test
    void shouldUseXmlProviderForProviderNo_whenApptProviderBlank() {
        request.setParameter("xml_provider", "111111|something");
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        BillingONFormDataAssembler assembler = new BillingONFormDataAssembler();
        BillingONFormViewModel model = assembler.assemble(loggedInInfo, request);

        assertThat(model.getProviderNo()).isEqualTo("111111");
    }

    @Test
    void shouldUseProviderViewForProviderNo_whenOnlyProviderViewSupplied() {
        request.setParameter("providerview", "222222");
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        BillingONFormDataAssembler assembler = new BillingONFormDataAssembler();
        BillingONFormViewModel model = assembler.assemble(loggedInInfo, request);

        assertThat(model.getProviderNo()).isEqualTo("222222");
    }
}
