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

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnReviewViewModel;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimLoader;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnReviewDiagPersister;
import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodeLoader;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingOnReviewValidator;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewQueryService;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;

/**
 * Unit tests for the pure-read part of {@link BillingOnReviewViewModelAssembler}.
 * The {@code addToPatientDx} side-effect was extracted to
 * {@link BillingOnReviewDiagPersister}; see
 * {@code BillingOnReviewDiagPersisterUnitTest} for those cases.
 *
 * @since 2026-04-24
 */
@DisplayName("BillingOnReviewViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingOnReviewViewModelAssemblerUnitTest extends CarlosUnitTestBase {

    @Mock
    private DemographicDao demographicDao;
    @Mock
    private ProviderDao providerDao;
    @Mock
    private BillingReviewQueryService reviewPrep;

    private BillingOnReviewViewModelAssembler assembler;
    private MockHttpServletRequest request;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        when(reviewPrep.getDxDescription(any())).thenReturn("Essential, benign hypertension");
        // The assembler unconditionally indexes into the result of these
        // two methods at lines 332-334; default null Mockito return NPEs.
        // Empty 3-slot arrays exercise the no-service-code path.
        @SuppressWarnings("unchecked")
        java.util.ArrayList<String>[] emptyVec = new java.util.ArrayList[]{
                new java.util.ArrayList<String>(),
                new java.util.ArrayList<String>(),
                new java.util.ArrayList<String>()};
        when(reviewPrep.getRequestFormCodeVec(any(), any(), any(), any())).thenReturn(emptyVec);
        when(reviewPrep.getRequestCodeVec(any(), any(), any(), any(), anyInt())).thenReturn(emptyVec);

        ServiceCodeLoader serviceCodeLoader = Mockito.mock(ServiceCodeLoader.class);
        when(serviceCodeLoader.getCodeDescByNames(any())).thenReturn(new java.util.Properties());
        registerMock(ServiceCodeLoader.class, serviceCodeLoader);
        BillingOnLookupService lookupService = Mockito.mock(BillingOnLookupService.class);
        registerMock(BillingOnLookupService.class, lookupService);
        SiteDao siteDao = Mockito.mock(SiteDao.class);
        registerMock(SiteDao.class, siteDao);

        BillingOnClaimLoader claimLoader = Mockito.mock(BillingOnClaimLoader.class);

        BillingOnReviewValidator stubValidator = Mockito.mock(BillingOnReviewValidator.class);
        when(stubValidator.validate(any(), any(), any())).thenReturn(
                new BillingOnReviewValidator.Result(java.util.Collections.emptyList(), true));
        io.github.carlos_emr.carlos.billings.ca.on.service.GstSettingsService gstSettingsService =
                Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.GstSettingsService.class);
        when(gstSettingsService.getCurrentPercent()).thenReturn(null);
        io.github.carlos_emr.carlos.billings.ca.on.service.GstReportService gstReport =
                Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.GstReportService.class);
        assembler = new BillingOnReviewViewModelAssembler(demographicDao, providerDao, reviewPrep, stubValidator,
                serviceCodeLoader, lookupService, siteDao, gstSettingsService, gstReport, claimLoader);
        request = new MockHttpServletRequest();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldExposeDxDescription_fromAssembler() {
        request.setParameter("dxCode", "401");

        BillingOnReviewViewModel m = assembler.assemble(request, null);
        assertThat(m.getDxCode()).isEqualTo("401");
        assertThat(m.getDxDesc()).isEqualTo("Essential, benign hypertension");
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

        BillingOnReviewViewModel m = assembler.assemble(request, null);

        assertThat(m.getErrorFlag()).isEqualTo("1");
        assertThat(m.getErrorMessage()).contains("does not have a valid DOB");
        assertThat(m.getErrorMessage()).doesNotContain("<", ">", "&nbsp;");
        assertThat(m.getWrongMessage()).doesNotContain("<", ">", "&nbsp;");
        assertThat(m.getReviewAlerts())
                .extracting(BillingOnReviewViewModel.ReviewAlert::level,
                        BillingOnReviewViewModel.ReviewAlert::message)
                .contains(tuple("danger", "Error: The patient does not have a valid DOB."));
    }

    @Test
    void shouldWarn_whenDemographicHinIsEmpty() {
        Demographic demo = new Demographic();
        demo.setFirstName("Jones");
        demo.setLastName("Jacky");
        demo.setSex("M");
        demo.setHin("");
        demo.setVer("");
        demo.setHcType("ON");
        demo.setYearOfBirth("1985");
        demo.setMonthOfBirth("06");
        demo.setDateOfBirth("15");
        when(demographicDao.getDemographic("1")).thenReturn(demo);
        request.setParameter("demographic_no", "1");

        BillingOnReviewViewModel m = assembler.assemble(request, null);

        assertThat(m.getWarningMessage()).contains("does not have a HIN");
        assertThat(m.getWarningMessage()).doesNotContain("<", ">", "&nbsp;");
        assertThat(m.getReviewAlerts())
                .extracting(BillingOnReviewViewModel.ReviewAlert::level,
                        BillingOnReviewViewModel.ReviewAlert::message)
                .contains(tuple("danger", "Warning: The patient does not have a HIN"));
    }

    @Test
    void shouldErrorWithBoldRedHtml_whenHinIsNull() {
        Demographic demo = new Demographic();
        demo.setFirstName("Jones");
        demo.setLastName("Jacky");
        demo.setSex("M");
        demo.setHin(null); // null vs empty triggers ERROR not warning per legacy logic
        demo.setVer("AB");
        demo.setHcType("ON");
        demo.setYearOfBirth("1985");
        demo.setMonthOfBirth("06");
        demo.setDateOfBirth("15");
        when(demographicDao.getDemographic("1")).thenReturn(demo);
        request.setParameter("demographic_no", "1");

        BillingOnReviewViewModel m = assembler.assemble(request, null);

        assertThat(m.getErrorFlag()).isEqualTo("1");
        assertThat(m.getErrorMessage()).contains("does not have a HIN");
        assertThat(m.getReviewAlerts())
                .extracting(BillingOnReviewViewModel.ReviewAlert::message)
                .contains("Error: The patient does not have a HIN");
    }

    @Test
    void shouldBuildPlainTextDemographicHeader_withoutHtmlEntities() {
        Demographic demo = new Demographic();
        demo.setFirstName("Jones");
        demo.setLastName("Jacky");
        demo.setSex("M");
        demo.setHin("9876543225");
        demo.setVer("AB");
        demo.setHcType("ON");
        demo.setYearOfBirth("1985");
        demo.setMonthOfBirth("06");
        demo.setDateOfBirth("15");
        when(demographicDao.getDemographic("1")).thenReturn(demo);
        request.setParameter("demographic_no", "1");

        BillingOnReviewViewModel m = assembler.assemble(request, null);

        assertThat(m.getDemoHeaderLine()).isEqualTo("DOB: 1985/06/15 HIN: 9876543225AB");
        assertThat(m.getDemoHeaderLine()).doesNotContain("&nbsp;");
    }

    @Test
    void shouldLoadProviderOhip_whenXmlProviderProvided() {
        Provider p = new Provider();
        p.setOhipNo("OHIP1");
        p.setRmaNo("RMA1");
        when(providerDao.getProvider("999998")).thenReturn(p);
        request.setParameter("xml_provider", "999998");

        BillingOnReviewViewModel m = assembler.assemble(request, null);

        assertThat(m.getProviderOhip()).isEqualTo("OHIP1");
        assertThat(m.getProviderRma()).isEqualTo("RMA1");
        assertThat(m.getProviderView()).isEqualTo("999998");
    }

    @Test
    void shouldStripPipeSuffix_fromXmlProviderBeforeDaoLookup() {
        Provider p = new Provider();
        p.setOhipNo("OHIP1");
        p.setRmaNo("RMA1");
        // billingON.jsp posts xml_provider as "providerNo|ohipNo"; assembler must
        // strip at the pipe before calling getProvider.
        when(providerDao.getProvider("999998")).thenReturn(p);
        request.setParameter("xml_provider", "999998|OHIP1");

        BillingOnReviewViewModel m = assembler.assemble(request, null);

        assertThat(m.getProviderOhip()).isEqualTo("OHIP1");
        assertThat(m.getProviderRma()).isEqualTo("RMA1");
        assertThat(m.getProviderView()).isEqualTo("999998");
    }

    @Test
    void shouldFallBackToProviderviewParam_whenXmlProviderMissing() {
        Provider p = new Provider();
        p.setOhipNo("OHIP1");
        when(providerDao.getProvider("888888")).thenReturn(p);
        request.setParameter("providerview", "888888");

        BillingOnReviewViewModel m = assembler.assemble(request, null);

        assertThat(m.getProviderView()).isEqualTo("888888");
        assertThat(m.getProviderOhip()).isEqualTo("OHIP1");
    }
}
