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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingEdtObecOutputSpecificationRecordDto;
import io.github.carlos_emr.carlos.commn.dao.BatchEligibilityDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.model.BatchEligibility;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the loop-skip semantics of
 * {@link BillingObecOutputApplyService#applyOutputSpec}. Each branch the
 * service guards (unparseable response code, response code in 50..59,
 * empty demographic match, null ver) was a silent corruption hazard
 * pre-fix; the assertions here lock the skip-rather-than-throw contract
 * so a future refactor doesn't reintroduce them as crashes that abort
 * the whole batch.
 *
 * <p>Spring's {@code @Transactional} rollback is AOP-driven and not
 * exercisable on a plain {@code new …()} instance — that boundary is
 * covered by the action-level integration test for
 * {@code BillingDocumentErrorReportUpload2Action}. Here we only verify
 * the in-loop branching that the service unit-owns.</p>
 *
 * @since 2026-04-30
 */
@DisplayName("BillingObecOutputApplyService loop-skip semantics")
@Tag("unit")
@Tag("billing")
class BillingObecOutputApplyServiceUnitTest {

    private BatchEligibilityDao batchEligibilityDao;
    private DemographicCustDao demographicCustDao;
    private DemographicManager demographicManager;
    private BillingObecOutputApplyService service;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        batchEligibilityDao = mock(BatchEligibilityDao.class);
        demographicCustDao = mock(DemographicCustDao.class);
        demographicManager = mock(DemographicManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        service = new BillingObecOutputApplyService(
                batchEligibilityDao, demographicCustDao, demographicManager);
    }

    private static BillingEdtObecOutputSpecificationRecordDto record(
            String hin, String version, String responseCode) {
        return new BillingEdtObecOutputSpecificationRecordDto(hin, version, responseCode);
    }

    private static Demographic demographic(int demoNo, String ver) {
        Demographic d = new Demographic();
        d.setDemographicNo(demoNo);
        d.setVer(ver);
        return d;
    }

    @Test
    void shouldSkipUnparseableResponseCode_andContinueLoop() {
        BillingEdtObecOutputSpecificationRecordDto bad = record("1234567890", "AB", "NOT_A_NUMBER");
        BillingEdtObecOutputSpecificationRecordDto good = record("9876543210", "AB", "100");

        BatchEligibility be = mock(BatchEligibility.class);
        when(be.getMOHResponse()).thenReturn("Invalid HIN");
        when(be.getReason()).thenReturn("Old version");
        when(batchEligibilityDao.find(100)).thenReturn(be);
        when(demographicManager.searchByHealthCard(eq(loggedInInfo), eq("9876543210")))
                .thenReturn(List.of(demographic(42, "AB")));

        DemographicCust cust = new DemographicCust();
        cust.setAlert("");
        when(demographicCustDao.find((Object) Integer.valueOf(42))).thenReturn(cust);

        service.applyOutputSpec(loggedInInfo, List.of(bad, good));

        // Bad row never resolved past parseInt; good row reached merge.
        verify(demographicManager, never())
                .searchByHealthCard(eq(loggedInInfo), eq("1234567890"));
        verify(demographicManager, times(1))
                .updateDemographic(eq(loggedInInfo), any(Demographic.class));
        verify(demographicCustDao, times(1)).merge(cust);
    }

    @Test
    void shouldSkipResponseCodeInPassThroughRange_50To59() {
        BillingEdtObecOutputSpecificationRecordDto skip = record("1111111111", "AB", "55");

        service.applyOutputSpec(loggedInInfo, List.of(skip));

        verify(batchEligibilityDao, never()).find(anyInt());
        verify(demographicManager, never())
                .searchByHealthCard(any(LoggedInInfo.class), anyString());
        verify(demographicManager, never())
                .updateDemographic(any(LoggedInInfo.class), any(Demographic.class));
    }

    @Test
    void shouldSkipRow_whenNoDemographicMatchesHin() {
        BillingEdtObecOutputSpecificationRecordDto row = record("0000000000", "AB", "100");
        when(batchEligibilityDao.find(100)).thenReturn(mock(BatchEligibility.class));
        when(demographicManager.searchByHealthCard(eq(loggedInInfo), eq("0000000000")))
                .thenReturn(List.of());

        service.applyOutputSpec(loggedInInfo, List.of(row));

        verify(demographicManager, never())
                .updateDemographic(any(LoggedInInfo.class), any(Demographic.class));
        verify(demographicCustDao, never()).merge(any(DemographicCust.class));
    }

    @Test
    void shouldSkipRow_whenDemographicVerIsNull() {
        BillingEdtObecOutputSpecificationRecordDto row = record("2222222222", "AB", "100");
        when(batchEligibilityDao.find(100)).thenReturn(mock(BatchEligibility.class));
        when(demographicManager.searchByHealthCard(eq(loggedInInfo), eq("2222222222")))
                .thenReturn(List.of(demographic(7, null)));

        service.applyOutputSpec(loggedInInfo, List.of(row));

        verify(demographicManager, never())
                .updateDemographic(any(LoggedInInfo.class), any(Demographic.class));
        verify(demographicCustDao, never()).merge(any(DemographicCust.class));
    }

    @Test
    void shouldSkipRow_whenBeanVersionIsNull() {
        BillingEdtObecOutputSpecificationRecordDto row = record("3333333333", null, "100");
        when(batchEligibilityDao.find(100)).thenReturn(mock(BatchEligibility.class));
        when(demographicManager.searchByHealthCard(eq(loggedInInfo), eq("3333333333")))
                .thenReturn(List.of(demographic(8, "AB")));

        service.applyOutputSpec(loggedInInfo, List.of(row));

        verify(demographicManager, never())
                .updateDemographic(any(LoggedInInfo.class), any(Demographic.class));
        verify(demographicCustDao, never()).merge(any(DemographicCust.class));
    }

    @Test
    void shouldSkipRow_whenVersionsDoNotMatch() {
        BillingEdtObecOutputSpecificationRecordDto row = record("4444444444", "ZZ", "100");
        when(batchEligibilityDao.find(100)).thenReturn(mock(BatchEligibility.class));
        when(demographicManager.searchByHealthCard(eq(loggedInInfo), eq("4444444444")))
                .thenReturn(List.of(demographic(9, "AB")));

        service.applyOutputSpec(loggedInInfo, List.of(row));

        verify(demographicManager, never())
                .updateDemographic(any(LoggedInInfo.class), any(Demographic.class));
        verify(demographicCustDao, never()).merge(any(DemographicCust.class));
    }

    @Test
    void shouldFlipVerToInvalidMarker_andAppendAlertReason_whenVersionsMatch() {
        BillingEdtObecOutputSpecificationRecordDto row = record("5555555555", "AB", "200");
        BatchEligibility be = mock(BatchEligibility.class);
        when(be.getMOHResponse()).thenReturn("Invalid HIN");
        when(be.getReason()).thenReturn("Card cancelled");
        when(batchEligibilityDao.find(200)).thenReturn(be);

        Demographic d = demographic(101, "AB");
        when(demographicManager.searchByHealthCard(eq(loggedInInfo), eq("5555555555")))
                .thenReturn(List.of(d));

        DemographicCust cust = new DemographicCust();
        cust.setAlert("existing-alert");
        when(demographicCustDao.find((Object) Integer.valueOf(101))).thenReturn(cust);

        service.applyOutputSpec(loggedInInfo, List.of(row));

        assertThat(d.getVer()).isEqualTo("##");
        verify(demographicManager).updateDemographic(eq(loggedInInfo), eq(d));

        assertThat(cust.getAlert())
                .contains("existing-alert")
                .contains("Invalid old version code: AB")
                .contains("Reason: Invalid HIN- Card cancelled")
                .contains("Response Code: 200");
        verify(demographicCustDao).merge(cust);
    }

    @Test
    void shouldHandleMixedBatch_skippingBadRowsWithoutAffectingGoodOnes() {
        BillingEdtObecOutputSpecificationRecordDto bad1 = record("1111111111", "AB", "X");
        BillingEdtObecOutputSpecificationRecordDto bad2 = record("2222222222", "AB", "55");
        BillingEdtObecOutputSpecificationRecordDto good = record("3333333333", "AB", "100");

        when(batchEligibilityDao.find(100)).thenReturn(mock(BatchEligibility.class));
        when(demographicManager.searchByHealthCard(eq(loggedInInfo), eq("3333333333")))
                .thenReturn(List.of(demographic(50, "AB")));
        when(demographicCustDao.find((Object) Integer.valueOf(50))).thenReturn(new DemographicCust());

        service.applyOutputSpec(loggedInInfo, List.of(bad1, bad2, good));

        verify(demographicManager, times(1))
                .updateDemographic(any(LoggedInInfo.class), any(Demographic.class));
        verify(demographicCustDao, times(1)).merge(any(DemographicCust.class));
    }
}
