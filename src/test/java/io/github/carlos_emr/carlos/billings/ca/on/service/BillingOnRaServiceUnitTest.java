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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingRaDetailDto;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.RaDetail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for {@link BillingOnRaService}, the RA (Remittance
 * Advice) processing service. The full surface (576 LOC) has a 200+ LOC
 * fixed-width parser ({@code importRAFile}) and complex {@code getRASummary}
 * iteration where a silent-failure review flagged the
 * partial-result swallow on DAO error. This suite covers the simpler
 * write/query methods and pins the silent-swallow contract on the summary
 * path so a future fix-closed change surfaces here.
 */
@DisplayName("BillingOnRaService")
@Tag("unit")
@Tag("billing")
class BillingOnRaServiceUnitTest {

    private RaDetailDao raDetailDao;
    private RaHeaderDao raHeaderDao;
    private BillingONCHeader1Dao cheader1Dao;
    private BillingOnRaService service;

    @BeforeEach
    void setUp() {
        raDetailDao = mock(RaDetailDao.class);
        raHeaderDao = mock(RaHeaderDao.class);
        cheader1Dao = mock(BillingONCHeader1Dao.class);
        service = new BillingOnRaService(raDetailDao, raHeaderDao, cheader1Dao);
    }

    // ---- addOneRADtRecord: write to ra_detail --------------------------

    @Test
    void shouldPersistRaDetailWithExpectedFields_whenAddOneRADtRecord() {
        BillingRaDetailDto dto = new BillingRaDetailDto();
        dto.setRaheader_no("99");
        dto.setProviderohip_no("012345");
        dto.setBilling_no("1001");
        dto.setService_code("A001A");
        dto.setService_count("1");
        dto.setHin("9876543225");
        dto.setAmountclaim("33.70");
        dto.setAmountpay("33.70");
        dto.setService_date("20260101");
        dto.setError_code("");
        dto.setBilltype("HCP");
        dto.setClaim_no("CLM-001");

        ArgumentCaptor<RaDetail> captor = ArgumentCaptor.forClass(RaDetail.class);
        // The persister returns the assigned id via JPA; legacy unboxes it
        // through r.getId(). We only verify the persist-call shape here
        // because the id setter route is JPA-driven and not under test.
        try {
            service.addOneRADtRecord(dto);
        } catch (NullPointerException expected) {
            // Returned-int unbox of a JPA-assigned id can NPE under mocks.
        }

        verify(raDetailDao).persist(captor.capture());
        RaDetail r = captor.getValue();
        assertThat(r.getRaHeaderNo()).isEqualTo(99);
        assertThat(r.getProviderOhipNo()).isEqualTo("012345");
        assertThat(r.getBillingNo()).isEqualTo(1001);
        assertThat(r.getServiceCode()).isEqualTo("A001A");
        assertThat(r.getServiceDate()).isEqualTo("20260101");
        assertThat(r.getBillType()).isEqualTo("HCP");
        assertThat(r.getClaimNo()).isEqualTo("CLM-001");
    }

    // ---- getPropBillNoRAHeaderNo: simple aggregate ---------------------

    @Test
    void shouldReturnBillingNoToHeaderMap_whenGetPropBillNoRAHeaderNo() {
        RaDetail r1 = new RaDetail();
        r1.setBillingNo(1001);
        RaDetail r2 = new RaDetail();
        r2.setBillingNo(1002);
        when(raDetailDao.findByRaHeaderNo(99)).thenReturn(List.of(r1, r2));

        Properties props = service.getPropBillNoRAHeaderNo("99");

        assertThat(props.getProperty("1001")).isEqualTo("99");
        assertThat(props.getProperty("1002")).isEqualTo("99");
    }

    @Test
    void shouldReturnEmptyProperties_whenNoRADetailsFound() {
        when(raDetailDao.findByRaHeaderNo(99)).thenReturn(Collections.emptyList());

        Properties props = service.getPropBillNoRAHeaderNo("99");

        assertThat(props).isEmpty();
    }

    // ---- getRAClaimNo4BillingNo ----------------------------------------

    @Test
    void shouldReturnLastClaimNo_whenMultipleDetailsForBillingNo() {
        RaDetail a = new RaDetail();
        a.setClaimNo("CLM-A");
        RaDetail b = new RaDetail();
        b.setClaimNo("CLM-B");
        when(raDetailDao.findByBillingNo(1001)).thenReturn(List.of(a, b));

        // Loops over results and assigns each — the last one wins.
        assertThat(service.getRAClaimNo4BillingNo("1001")).isEqualTo("CLM-B");
    }

    @Test
    void shouldReturnEmptyString_whenNoClaimsForBillingNo() {
        when(raDetailDao.findByBillingNo(1001)).thenReturn(Collections.emptyList());

        assertThat(service.getRAClaimNo4BillingNo("1001")).isEmpty();
    }

    // ---- getRABillingNo4Code: dedupe via Set ---------------------------

    @Test
    void shouldDedupeBillingNos_whenGetRABillingNo4Code() {
        RaDetail a = new RaDetail();
        a.setBillingNo(1001);
        RaDetail b = new RaDetail();
        b.setBillingNo(1001); // dupe
        RaDetail c = new RaDetail();
        c.setBillingNo(1002);
        when(raDetailDao.findByRaHeaderNoAndServiceCodes(99, Arrays.asList("A001A")))
                .thenReturn(List.of(a, b, c));

        List<String> ret = service.getRABillingNo4Code("99", "A001A");

        assertThat(ret).containsExactlyInAnyOrder("1001", "1002");
    }

    // ---- updateBillingStatus -------------------------------------------

    @Test
    void shouldFlipStatusAndMerge_whenUpdateBillingStatusOnActiveHeader() {
        BillingONCHeader1 header = mock(BillingONCHeader1.class);
        when(header.isActive()).thenReturn(true);
        when(cheader1Dao.find(42)).thenReturn(header);

        boolean ret = service.updateBillingStatus("42", "S");

        assertThat(ret).isTrue();
        verify(header).setStatus("S");
        verify(cheader1Dao).merge(header);
    }

    @Test
    void shouldNotMutate_whenUpdateBillingStatusOnInactiveHeader() {
        BillingONCHeader1 header = mock(BillingONCHeader1.class);
        when(header.isActive()).thenReturn(false);
        when(cheader1Dao.find(42)).thenReturn(header);

        boolean ret = service.updateBillingStatus("42", "S");

        assertThat(ret).isTrue();
        verify(header, never()).setStatus(any());
        verify(cheader1Dao, never()).merge(any(BillingONCHeader1.class));
    }

    @Test
    void shouldReturnTrue_whenUpdateBillingStatusHeaderNotFound() {
        // Legacy contract: returns true even when header is missing. The
        // test pins the silent-success behavior so a future fix-closed
        // change surfaces as a test break.
        when(cheader1Dao.find(42)).thenReturn(null);

        boolean ret = service.updateBillingStatus("42", "S");

        assertThat(ret).isTrue();
        verify(cheader1Dao, never()).merge(any(BillingONCHeader1.class));
    }

    // ---- getRASummary: pin the silent-swallow contract -----------------

    @Test
    void shouldAppendLoadFailureMarker_whenGetRASummaryHitsDaoError() {
        // Round-6 P1-8 contract change: when the outer catch fires, the
        // service appends a marker Properties row with LOAD_FAILURE_MARKER=true
        // so the downstream consumer (BillingRaReportService.getRASummary)
        // can detect the partial load and bump xml_partial_count, which
        // ultimately blocks OnRaSummaryTotalsService.mergeTotals from
        // overwriting RaHeader.content with the partial total.
        when(raDetailDao.findByRaHeaderNoAndProviderOhipNo(anyInt(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("simulated DAO failure"));

        List<Properties> result = service.getRASummary("99", "012345");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperty(BillingOnRaService.LOAD_FAILURE_MARKER))
                .isEqualTo("true");
    }

    @Test
    void shouldReturnEmptyList_whenGetRASummaryHasNoDetails() {
        when(raDetailDao.findByRaHeaderNoAndProviderOhipNo(99, "012345"))
                .thenReturn(Collections.emptyList());

        List<Properties> result = service.getRASummary("99", "012345");

        assertThat(result).isEmpty();
    }
}
