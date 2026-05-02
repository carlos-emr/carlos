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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.bc.decisionSupport.BillingGuidelines;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingAdmissionDateLoader;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFormViewModel;
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
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimLoader;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Smoke unit tests for {@link BillingOnFormViewModelAssembler}.
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
@DisplayName("BillingOnFormViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingOnFormViewModelAssemblerUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest request;
    private LoggedInInfo loggedInInfo;
    private BillingOnFormViewModelAssembler assembler;
    private BillingAdmissionDateLoader admissionDateLoader;
    private MockedStatic<BillingGuidelines> billingGuidelinesMock;
    private BillingGuidelines billingGuidelines;

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

        // BillingOnLookupService is reached via SpringUtils.getBean at
        // BillingOnFormViewModelAssembler:406 for the favourites dropdown lookup.
        // Mock the service directly — registering its inner DAOs has no
        // effect because the SpringUtils intercept looks the bean up in the
        // mock registry and never constructs it.
        BillingOnLookupService billingONLookupService = Mockito.mock(BillingOnLookupService.class);
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

        // SiteDao + ClinicNbrDao added so the new
        // BillingOnFormSiteContextComposer (multisite + RMA / clinic-nbr
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
        registerMock(BillingOnLookupService.class, billingONLookupService);
        registerMock(BillingOnClaimLoader.class, Mockito.mock(BillingOnClaimLoader.class));

        when(billingONLookupService.getBillingFavouriteList()).thenReturn(Collections.emptyList());

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
        billingGuidelines = Mockito.mock(BillingGuidelines.class);
        billingGuidelinesMock = Mockito.mockStatic(BillingGuidelines.class);
        billingGuidelinesMock.when(BillingGuidelines::getInstance).thenReturn(billingGuidelines);
        when(billingGuidelines.evaluateAndGetConsequences(any(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        admissionDateLoader = Mockito.mock(BillingAdmissionDateLoader.class);

        assembler = new BillingOnFormViewModelAssembler(
                dxresearchDAO,
                userPropertyDAO,
                providerDao,
                billingONLookupService,
                Mockito.mock(BillingOnClaimLoader.class),
                new BillingOnFormDemographicLoader(demographicManager, professionalSpecialistDao),
                new BillingOnFormBillFormResolver(ctlBillingServiceDao, providerPreferenceDao, myGroupDao),
                new BillingOnFormServiceGridComposer(
                        ctlBillingServiceDao, billingServiceDao, ctlBillingServicePremiumDao,
                        cssStylesDAO, codeFilterManager, ctlBillingTypeDao, diagnosticCodeDao),
                new BillingOnFormSiteContextComposer(
                        siteDao, oscarAppointmentDao, clinicNbrDao, providerDao),
                Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingSiteIdService.class),
                admissionDateLoader);
    }

    @AfterEach
    void tearDown() {
        if (billingGuidelinesMock != null) {
            billingGuidelinesMock.close();
        }
    }

    @Test
    void shouldReturnNonNullModel_whenAssembledWithMinimalContext() {
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        // assembler built in setUp
        BillingOnFormViewModel model = assembler.assemble(request, loggedInInfo);

        assertThat(model).isNotNull();
        assertThat(model.getRequestContext().userNo()).isEqualTo("999998");
        assertThat(model.getRequestContext().demographicNo()).isEqualTo("1");
        assertThat(model.getRequestContext().ctlBillForm()).isEqualTo("GP");
        assertThat(model.getUserNo()).isEqualTo(model.getRequestContext().userNo());
        assertThat(model.getDemographicNo()).isEqualTo(model.getRequestContext().demographicNo());
        assertThat(model.getCtlBillForm()).isEqualTo(model.getRequestContext().ctlBillForm());
    }

    @Test
    void shouldDefaultDemoHcType_toONWhenDemographicMissing() {
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        // assembler built in setUp
        BillingOnFormViewModel model = assembler.assemble(request, loggedInInfo);

        assertThat(model.getDemographic().hcType()).isEqualTo("ON");
        assertThat(model.getDemoHcType()).isEqualTo(model.getDemographic().hcType());
    }

    @Test
    void shouldFlagInvalidDob_whenDemographicMissing() {
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        // assembler built in setUp
        BillingOnFormViewModel model = assembler.assemble(request, loggedInInfo);

        assertThat(model.getMessages().errorFlag()).isEqualTo("1");
        assertThat(model.getMessages().errorMessage()).contains("does not have a valid DOB");
        assertThat(model.getErrorFlag()).isEqualTo(model.getMessages().errorFlag());
        assertThat(model.getErrorMsg()).isEqualTo(model.getMessages().errorMessage());
    }

    @Test
    void shouldExposeProviderViewFromXmlProviderParam_whenSupplied() {
        request.setParameter("xml_provider", "111111|something");
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        // assembler built in setUp
        BillingOnFormViewModel model = assembler.assemble(request, loggedInInfo);

        // The pipe is stripped — JSP's behaviour preserved.
        assertThat(model.getProviderPanel().providerView()).isEqualTo("111111");
        assertThat(model.getProviderView()).isEqualTo(model.getProviderPanel().providerView());
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

        // assembler built in setUp
        BillingOnFormViewModel model = assembler.assemble(request, loggedInInfo);

        assertThat(model.getRequestContext().providerNo()).isEqualTo("999998");
        assertThat(model.getProviderNo()).isEqualTo(model.getRequestContext().providerNo());
    }

    @Test
    void shouldUseXmlProviderForProviderNo_whenApptProviderBlank() {
        request.setParameter("xml_provider", "111111|something");
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        // assembler built in setUp
        BillingOnFormViewModel model = assembler.assemble(request, loggedInInfo);

        assertThat(model.getRequestContext().providerNo()).isEqualTo("111111");
        assertThat(model.getProviderNo()).isEqualTo(model.getRequestContext().providerNo());
    }

    @Test
    void shouldUseProviderViewForProviderNo_whenOnlyProviderViewSupplied() {
        request.setParameter("providerview", "222222");
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        // assembler built in setUp
        BillingOnFormViewModel model = assembler.assemble(request, loggedInInfo);

        assertThat(model.getRequestContext().providerNo()).isEqualTo("222222");
        assertThat(model.getProviderNo()).isEqualTo(model.getRequestContext().providerNo());
    }

    @Test
    void shouldNotLogPatientOrProviderIdentifiers_whenDroolsRecommendationsFail() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on/assembler/BillingOnFormViewModelAssembler.java"));

        assertThat(source).doesNotContain("Drools billing-guidelines evaluation failed for demo={}");
        assertThat(source).doesNotContain("LogSanitizer.sanitize(demoNo), userNo, e");
        assertThat(source).doesNotContain("OutOfMemoryError-adjacent");
    }

    @Test
    void shouldFlagRecommendationsUnavailable_whenDroolsEvaluationFails() throws Exception {
        request.setParameter("demographic_no", "1");
        request.setParameter("appointment_no", "0");
        request.setParameter("service_date", "2026-04-24");
        request.setParameter("billForm", "GP");

        when(billingGuidelines.evaluateAndGetConsequences(any(), eq("1"), eq("999998")))
                .thenThrow(new RuntimeException("rules unavailable"));

        BillingOnFormViewModel model = assembler.assemble(request, loggedInInfo);

        assertThat(model.isRecommendationsUnavailable()).isTrue();
        assertThat(model.getDegradationFlags()).contains(
                BillingOnFormViewModel.DegradationFlag.RECOMMENDATIONS_UNAVAILABLE);
    }

    @Test
    void shouldFlagAdmissionDateUnavailable_whenInpatientAdmissionLookupFails() {
        Object original = CarlosProperties.getInstance().get("inPatient");
        try {
            CarlosProperties.getInstance().setProperty("inPatient", "yes");
            request.setParameter("demographic_no", "1");
            request.setParameter("appointment_no", "0");
            request.setParameter("service_date", "2026-04-24");
            request.setParameter("billForm", "GP");
            when(admissionDateLoader.getAdmissionDate(any(), eq("1")))
                    .thenThrow(new RuntimeException("admission lookup failed"));

            BillingOnFormViewModel model = assembler.assemble(request, loggedInInfo);

            assertThat(model.isAdmissionDateUnavailable()).isTrue();
            assertThat(model.getDegradationFlags()).contains(
                    BillingOnFormViewModel.DegradationFlag.ADMISSION_DATE_UNAVAILABLE);
        } finally {
            if (original == null) {
                CarlosProperties.getInstance().remove("inPatient");
            } else {
                CarlosProperties.getInstance().put("inPatient", original);
            }
        }
    }

    @Test
    void shouldNotInstantiateSpringServicesDirectly_forSpringContract() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on/assembler/BillingOnFormViewModelAssembler.java"));

        assertThat(source)
                .doesNotContain("new BillingSiteIdService()")
                .doesNotContain("new io.github.carlos_emr.carlos.demographic.data.DemographicData()");
    }
}
