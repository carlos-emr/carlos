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

import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingShortcutPg1ViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimLoader;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingShortcutPg1ViewModelAssembler}.
 *
 * <p>Exercises the demographic-driven validation path, provider/clinic list
 * assembly, and the default-resolution helper. Service-code grid prep is
 * covered by integration tests that have a real DB; here we just verify the
 * empty-grid case.</p>
 *
 * @since 2026-04-24
 */
@DisplayName("BillingShortcutPg1ViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingShortcutPg1ViewModelAssemblerUnitTest extends CarlosUnitTestBase {

    @Mock
    private DemographicDao demographicDao;
    @Mock
    private ProviderDao providerDao;
    @Mock
    private BillingDao billingDao;
    @Mock
    private BillingDetailDao billingDetailDao;
    @Mock
    private BillingServiceDao billingServiceDao;
    @Mock
    private CtlBillingServicePremiumDao ctlBillingServicePremiumDao;
    @Mock
    private ClinicLocationDao clinicLocationDao;
    @Mock
    private ProfessionalSpecialistDao professionalSpecialistDao;
    @Mock
    private BillingOnClaimLoader billingReviewImpl;

    private BillingShortcutPg1ViewModelAssembler assembler;
    private MockHttpServletRequest request;
    private LoggedInInfo loggedInInfo;
    private AutoCloseable mockitoCloseable;
    private String previousIsNewOnBilling;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        previousIsNewOnBilling = CarlosProperties.getInstance().getProperty("isNewONbilling", "");
        CarlosProperties.getInstance().setProperty("isNewONbilling", "false");

        // Default: empty everything (the assembler shouldn't NPE on no data).
        when(billingDao.findActiveBillingsByDemoNo(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(providerDao.getDoctorsWithOhip()).thenReturn(Collections.emptyList());
        when(clinicLocationDao.findAll()).thenReturn(Collections.emptyList());
        when(billingServiceDao.findBillingServiceAndCtlBillingServiceByMagic(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(billingReviewImpl.getBillingHist(any(), anyInt(), anyInt(), any()))
                .thenReturn(Collections.emptyList());

        assembler = new BillingShortcutPg1ViewModelAssembler(
                demographicDao, providerDao, billingDao, billingDetailDao,
                billingServiceDao, ctlBillingServicePremiumDao,
                clinicLocationDao, professionalSpecialistDao,
                mock(io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao.class),
                mock(io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao.class),
                mock(io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao.class),
                billingReviewImpl);

        request = new MockHttpServletRequest();
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    @AfterEach
    void tearDown() throws Exception {
        CarlosProperties.getInstance().setProperty("isNewONbilling", previousIsNewOnBilling);
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldHardCodeVisitTypeTo02_forHospitalBilling() {
        request.setParameter("demographic_no", "1");
        when(demographicDao.getDemographic("1")).thenReturn(null);

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        // The assembler is the hospital-billing shortcut, so visit type 02 is forced
        // unless an xml_visittype param overrides it.
        assertThat(m.getVisitType()).isEqualTo("02");
    }

    @Test
    void shouldHonorXmlVisittypeOverride_overHospitalDefault() {
        request.setParameter("demographic_no", "1");
        request.setParameter("xml_visittype", "00");
        when(demographicDao.getDemographic("1")).thenReturn(null);

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);
        assertThat(m.getVisitType()).isEqualTo("00");
    }

    @Test
    void shouldNotNpe_whenDemographicMissing() {
        when(demographicDao.getDemographic(any())).thenReturn(null);
        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);
        assertThat(m).isNotNull();
        assertThat(m.getDemoFirst()).isEmpty();
        assertThat(m.getDemoLast()).isEmpty();
        assertThat(m.getDemoHin()).isEmpty();
    }

    @Test
    void shouldFlagInvalidDob_andSurfaceErrorMessage() {
        Demographic demo = new Demographic();
        demo.setFirstName("Jones");
        demo.setLastName("Jacky");
        demo.setSex("M");
        demo.setHin("9876543225");
        demo.setVer("AB");
        demo.setHcType("ON");
        demo.setYearOfBirth("1985");
        demo.setMonthOfBirth("06");
        // Missing dateOfBirth -> dob length != 8 -> errorFlag set.
        when(demographicDao.getDemographic("1")).thenReturn(demo);
        request.setParameter("demographic_no", "1");

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        assertThat(m.getErrorFlag()).isEqualTo("1");
        assertThat(m.getErrorMessage()).contains("does not have a valid DOB");
    }

    @Test
    void shouldMapDemographicSexCodes_toScriptletOneOrTwo() {
        Demographic demo = new Demographic();
        demo.setSex("M");
        demo.setHin("9876543225");
        demo.setVer("AB");
        demo.setYearOfBirth("1985");
        demo.setMonthOfBirth("06");
        demo.setDateOfBirth("15");
        when(demographicDao.getDemographic("1")).thenReturn(demo);
        request.setParameter("demographic_no", "1");

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);
        assertThat(m.getDemoSex()).isEqualTo("1");
        assertThat(m.getDemoDob()).isEqualTo("19850615");
    }

    @Test
    void shouldDefaultHcType_toONWhenMissing() {
        Demographic demo = new Demographic();
        demo.setSex("F");
        demo.setHin("9876543225");
        demo.setVer("AB");
        demo.setYearOfBirth("1985");
        demo.setMonthOfBirth("06");
        demo.setDateOfBirth("15");
        when(demographicDao.getDemographic("1")).thenReturn(demo);
        request.setParameter("demographic_no", "1");

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);
        assertThat(m.getDemoHcType()).isEqualTo("ON");
        assertThat(m.getDemoSex()).isEqualTo("2");
    }

    @Test
    void shouldBuildProviderList_fromDoctorsWithOhip() {
        Provider p = new Provider();
        p.setLastName("Doe");
        p.setFirstName("Jane");
        p.setProviderNo("999998");
        when(providerDao.getDoctorsWithOhip()).thenReturn(List.of(p));

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        assertThat(m.getProviders()).hasSize(1);
        assertThat(m.getProviders().get(0).getProperty("last_name")).isEqualTo("Doe");
        assertThat(m.getProviders().get(0).getProperty("first_name")).isEqualTo("Jane");
        assertThat(m.getProviders().get(0).getProperty("proOHIP")).isEqualTo("999998");
    }

    @Test
    void shouldBuildClinicLocationList_fromClinicLocationDao() {
        ClinicLocation loc = new ClinicLocation();
        loc.setClinicLocationName("Main Clinic");
        loc.setClinicLocationNo("1000");
        when(clinicLocationDao.findAll()).thenReturn(List.of(loc));

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        assertThat(m.getClinicLocations()).hasSize(1);
        assertThat(m.getClinicLocations().get(0).getProperty("clinic_location_name")).isEqualTo("Main Clinic");
        assertThat(m.getClinicLocations().get(0).getProperty("clinic_location_no")).isEqualTo("1000");
    }

    @Test
    void shouldEchoDxCodeFromRequestParam_whenProvided() {
        request.setParameter("dxCode", "401");
        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);
        assertThat(m.getDxCode()).isEqualTo("401");
    }

    @Test
    void shouldUseProviderViewParamOverUserProviderNo_whenXmlProviderProvided() {
        request.setParameter("xml_provider", "111111");
        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);
        assertThat(m.getProviderView()).isEqualTo("111111");
    }

    /**
     * The provider picker emits {@code xml_provider} as
     * {@code "<providerNo>|<ohipNo>"}; the assembler must strip the suffix
     * so downstream {@code ProviderDao.getProvider(providerView)} doesn't
     * receive a pipe-delimited token.
     */
    @Test
    void shouldStripPipeSuffix_fromXmlProvider() {
        request.setParameter("xml_provider", "111111|OHIP1234");

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        assertThat(m.getProviderView()).isEqualTo("111111");
    }

    /**
     * Regression armor for the {@code xml_provider == ""} clobber bug. An
     * empty {@code xml_provider} param (e.g., a stale form re-submit) must
     * NOT overwrite a populated {@code providerview} — it should fall
     * through to the providerview value.
     */
    @Test
    void shouldNotClobberProviderview_whenXmlProviderIsEmpty() {
        request.setParameter("xml_provider", "");
        request.setParameter("providerview", "111111");

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        assertThat(m.getProviderView()).isEqualTo("111111");
    }

    /**
     * When neither {@code xml_provider} nor {@code providerview} is supplied
     * the assembler falls back to the logged-in provider passed by
     * {@code ViewBillingShortcutPg1View2Action}.
     */
    @Test
    void shouldFallBackToUserProviderNo_whenBothParamsAbsent() {
        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        assertThat(m.getProviderView()).isEqualTo("999998");
    }

    @Test
    void shouldFlagPartialHistory_whenLegacyBillingRowIsSkipped() {
        request.setParameter("demographic_no", "1");
        Billing good = legacyBillingRow(1);
        Billing malformed = mock(Billing.class);
        when(malformed.getId()).thenReturn(2);
        when(malformed.getVisitDate()).thenThrow(new RuntimeException("bad visit date"));
        when(billingDao.findActiveBillingsByDemoNo(1, 5)).thenReturn(List.of(good, malformed));
        when(billingDetailDao.findByBillingNo(Integer.valueOf(1))).thenReturn(List.of(detail("A001A", "401", "1")));

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        assertThat(m.isHistoryUnavailable()).isFalse();
        assertThat(m.isHistoryPartial()).isTrue();
        assertThat(m.getHistoryPartialRowCount()).isEqualTo(1);
        assertThat(m.getBillingHistory()).hasSize(1);
        assertThat(m.getBillingHistory().get(0).getProperty("billing_no")).isEqualTo("1");
    }

    @Test
    void shouldFlagPartialHistory_whenLegacyDetailLookupIsSkipped() {
        request.setParameter("demographic_no", "1");
        Billing billing = legacyBillingRow(1);
        when(billingDao.findActiveBillingsByDemoNo(1, 5)).thenReturn(List.of(billing));
        when(billingDetailDao.findByBillingNo(Integer.valueOf(1))).thenThrow(new RuntimeException("detail lookup failed"));

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        assertThat(m.isHistoryUnavailable()).isFalse();
        assertThat(m.isHistoryPartial()).isTrue();
        assertThat(m.getHistoryPartialRowCount()).isEqualTo(1);
        assertThat(m.getBillingHistory()).hasSize(1);
        assertThat(m.getBillingHistoryDetails()).hasSize(1);
        assertThat(m.getBillingHistoryDetails().get(0).getProperty("service_code")).isEmpty();
    }

    @Test
    void shouldFlagPartialHistory_whenNewOnBillingPairIsSkipped() {
        String previous = CarlosProperties.getInstance().getProperty("isNewONbilling", "");
        CarlosProperties.getInstance().setProperty("isNewONbilling", "true");
        try {
            request.setParameter("demographic_no", "1");
            BillingClaimHeaderDto goodHeader = newClaimHeader("10");
            BillingClaimItemDto goodItem = newClaimItem("A001A", "401");
            when(billingReviewImpl.getBillingHist(eq("1"), eq(5), eq(0), any()))
                    .thenReturn(List.of(goodHeader, goodItem, "not-a-header", goodItem));

            BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

            assertThat(m.isHistoryUnavailable()).isFalse();
            assertThat(m.isHistoryPartial()).isTrue();
            assertThat(m.getHistoryPartialRowCount()).isEqualTo(1);
            assertThat(m.getBillingHistory()).hasSize(1);
            assertThat(m.getBillingHistoryDetails()).hasSize(1);
        } finally {
            if (previous == null) {
                CarlosProperties.getInstance().remove("isNewONbilling");
            } else {
                CarlosProperties.getInstance().setProperty("isNewONbilling", previous);
            }
        }
    }

    @Test
    void shouldOmitDemographicNumber_whenNewOnBillingPairShapeRegresses() {
        String previous = CarlosProperties.getInstance().getProperty("isNewONbilling", "");
        CarlosProperties.getInstance().setProperty("isNewONbilling", "true");
        try {
            String maliciousDemoNo = "1\r\nforged-demo";
            request.setParameter("demographic_no", maliciousDemoNo);
            BillingClaimItemDto goodItem = newClaimItem("A001A", "401");
            when(billingReviewImpl.getBillingHist(eq(maliciousDemoNo), eq(5), eq(0), any()))
                    .thenReturn(List.of("not-a-header", goodItem));

            try (LogCapture capture = LogCapture.forLogger(BillingShortcutPg1ViewModelAssembler.class)) {
                BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

                assertThat(m.isHistoryPartial()).isTrue();
                assertThat(m.getHistoryPartialRowCount()).isEqualTo(1);
                String logged = capture.messages().stream()
                        .filter(message -> message.contains("data-shape regression"))
                        .findFirst()
                        .orElseThrow();
                assertThat(logged).doesNotContain("\r").doesNotContain("\n");
                assertThat(logged).doesNotContain("1\r\nforged-demo", "1\\r\\nforged-demo", "forged-demo");
            }
        } finally {
            if (previous == null) {
                CarlosProperties.getInstance().remove("isNewONbilling");
            } else {
                CarlosProperties.getInstance().setProperty("isNewONbilling", previous);
            }
        }
    }

    @Test
    void shouldKeepPartialHistoryFalse_whenOuterHistoryLookupFails() {
        request.setParameter("demographic_no", "1");
        when(billingDao.findActiveBillingsByDemoNo(1, 5)).thenThrow(new RuntimeException("database down"));

        BillingShortcutPg1ViewModel m = assembler.assemble(request, loggedInInfo);

        assertThat(m.isHistoryUnavailable()).isTrue();
        assertThat(m.isHistoryPartial()).isFalse();
        assertThat(m.getHistoryPartialRowCount()).isZero();
        assertThat(m.getBillingHistory()).isEmpty();
        assertThat(m.getBillingHistoryDetails()).isEmpty();
    }

    private Billing legacyBillingRow(Integer id) {
        Billing billing = mock(Billing.class);
        when(billing.getId()).thenReturn(id);
        when(billing.getVisitDate()).thenReturn(new Date());
        when(billing.getBillingDate()).thenReturn(new Date());
        when(billing.getUpdateDate()).thenReturn(new Date());
        when(billing.getVisitType()).thenReturn("00");
        when(billing.getClinicRefCode()).thenReturn("clinic");
        when(billing.getContent()).thenReturn("");
        return billing;
    }

    private BillingDetail detail(String serviceCode, String diagnosticCode, String units) {
        BillingDetail detail = new BillingDetail();
        detail.setServiceCode(serviceCode);
        detail.setDiagnosticCode(diagnosticCode);
        detail.setBillingUnit(units);
        return detail;
    }

    private BillingClaimHeaderDto newClaimHeader(String id) {
        BillingClaimHeaderDto header = new BillingClaimHeaderDto();
        header = header.withId(id);
        header = header.withBillingDate("2026-04-30");
        header = header.withAdmissionDate("2026-04-29");
        header = header.withVisitType("00");
        header = header.withFacilityNumber("clinic");
        header = header.withUpdateDateTime("2026-04-30 12:00:00");
        return header;
    }

    private BillingClaimItemDto newClaimItem(String serviceCode, String dx) {
        BillingClaimItemDto item = new BillingClaimItemDto();
        item = item.withServiceCode(serviceCode);
        item = item.withDx(dx);
        return item;
    }
}
