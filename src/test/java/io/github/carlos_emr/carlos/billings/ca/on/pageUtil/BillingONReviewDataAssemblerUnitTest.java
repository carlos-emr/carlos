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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONReviewViewModel;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingONReviewDataAssembler}.
 *
 * <p>Locks down the demographic-driven validation paths and the
 * {@code addToPatientDx} side-effect that previously lived inside
 * {@code billingONReview.jsp}'s top scriptlet.</p>
 *
 * @since 2026-04-24
 */
@DisplayName("BillingONReviewDataAssembler")
@Tag("unit")
@Tag("billing")
class BillingONReviewDataAssemblerUnitTest extends CarlosUnitTestBase {

    @Mock
    private DemographicDao demographicDao;
    @Mock
    private ProviderDao providerDao;
    @Mock
    private DxresearchDAO dxresearchDAO;
    @Mock
    private BillingReviewPrep reviewPrep;

    private BillingONReviewDataAssembler assembler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(reviewPrep.getDxDescription(any())).thenReturn("Essential, benign hypertension");
        assembler = new BillingONReviewDataAssembler(
                demographicDao, providerDao, dxresearchDAO, reviewPrep);
        request = new MockHttpServletRequest();
    }

    @Test
    void shouldNotPersistDx_whenAddToPatientDxNotRequested() {
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "1");

        assembler.assemble(request, "999998");

        verify(dxresearchDAO, never()).save(any());
    }

    @Test
    void shouldPersistDx_whenAddToPatientDxRequested() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("codeMatchToPatientDx", "");
        request.setParameter("demographic_no", "1");

        assembler.assemble(request, "999998");

        ArgumentCaptor<Dxresearch> captor = ArgumentCaptor.forClass(Dxresearch.class);
        verify(dxresearchDAO, times(1)).save(captor.capture());
        Dxresearch saved = captor.getValue();
        assertThat(saved.getDemographicNo()).isEqualTo(1);
        assertThat(saved.getDxresearchCode()).isEqualTo("401");
        assertThat(saved.getCodingSystem()).isEqualTo("icd9");
        assertThat(saved.getProviderNo()).isEqualTo("999998");
    }

    @Test
    void shouldUseCodeMatchToPatientDx_whenSupplied_overDxCode() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("codeMatchToPatientDx", "401.1");
        request.setParameter("demographic_no", "1");

        assembler.assemble(request, "999998");

        ArgumentCaptor<Dxresearch> captor = ArgumentCaptor.forClass(Dxresearch.class);
        verify(dxresearchDAO, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDxresearchCode()).isEqualTo("401.1");
    }

    @Test
    void shouldNotPersistDx_whenDemoNoMissing() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        // demographic_no missing

        assembler.assemble(request, "999998");

        verify(dxresearchDAO, never()).save(any());
    }

    @Test
    void shouldNotPersistDx_whenBothDxFieldsEmpty() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("demographic_no", "1");

        assembler.assemble(request, "999998");

        verify(dxresearchDAO, never()).save(any());
    }

    @Test
    void shouldExposeDxDescriptionFromBillingReviewPrep() {
        request.setParameter("dxCode", "401");

        BillingONReviewViewModel m = assembler.assemble(request, "999998");
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

        BillingONReviewViewModel m = assembler.assemble(request, "999998");

        assertThat(m.getErrorFlag()).isEqualTo("1");
        assertThat(m.getErrorMessage()).contains("does not have a valid DOB");
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

        BillingONReviewViewModel m = assembler.assemble(request, "999998");

        assertThat(m.getWarningMessage()).contains("does not have a HIN");
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

        BillingONReviewViewModel m = assembler.assemble(request, "999998");

        assertThat(m.getErrorFlag()).isEqualTo("1");
        assertThat(m.getErrorMessage()).contains("does not have a HIN");
    }

    @Test
    void shouldLoadProviderOhip_whenXmlProviderProvided() {
        Provider p = new Provider();
        p.setOhipNo("OHIP1");
        p.setRmaNo("RMA1");
        when(providerDao.getProvider("999998")).thenReturn(p);
        request.setParameter("xml_provider", "999998");

        BillingONReviewViewModel m = assembler.assemble(request, "111111");

        assertThat(m.getProviderOhip()).isEqualTo("OHIP1");
        assertThat(m.getProviderRma()).isEqualTo("RMA1");
        assertThat(m.getProviderView()).isEqualTo("999998");
    }
}
