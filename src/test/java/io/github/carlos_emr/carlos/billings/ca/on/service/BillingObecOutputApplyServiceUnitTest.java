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

        BillingObecOutputApplyService.ApplyResult result =
                service.applyOutputSpec(loggedInInfo, List.of(bad, good));

        // Bad row never resolved past parseInt; good row reached merge.
        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.reasons()).anySatisfy(reason ->
                assertThat(reason).contains("unparseable response code"));
        verify(demographicManager, never())
                .searchByHealthCard(eq(loggedInInfo), eq("1234567890"));
        verify(demographicManager, times(1))
                .updateDemographic(eq(loggedInInfo), any(Demographic.class));
        verify(demographicCustDao, times(1)).merge(cust);
    }

    @Test
    void shouldSkipResponseCodeInPassThroughRange_whenResponseCodeIs50To59() {
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
    void shouldFlipVerToInvalidMarker_andAppendAlertReasonWhenVersionsMatch() {
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

        BillingObecOutputApplyService.ApplyResult result =
                service.applyOutputSpec(loggedInInfo, List.of(bad1, bad2, good));

        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.reasons()).hasSize(2);
        verify(demographicManager, times(1))
                .updateDemographic(any(LoggedInInfo.class), any(Demographic.class));
        verify(demographicCustDao, times(1)).merge(any(DemographicCust.class));
    }

    @Test
    void shouldNotExposeRawHinInSkipReasons_forSecurityContract() {
        BillingEdtObecOutputSpecificationRecordDto malformed = record("1111111111", "AB", "X");
        BillingEdtObecOutputSpecificationRecordDto passThrough = record("2222222222", "AB", "55");
        BillingEdtObecOutputSpecificationRecordDto noMatch = record("3333333333", "AB", "100");
        BillingEdtObecOutputSpecificationRecordDto mismatch = record("4444444444", "ZZ", "101");

        when(batchEligibilityDao.find(100)).thenReturn(mock(BatchEligibility.class));
        when(batchEligibilityDao.find(101)).thenReturn(mock(BatchEligibility.class));
        when(demographicManager.searchByHealthCard(eq(loggedInInfo), eq("3333333333")))
                .thenReturn(List.of());
        when(demographicManager.searchByHealthCard(eq(loggedInInfo), eq("4444444444")))
                .thenReturn(List.of(demographic(9, "AB")));

        BillingObecOutputApplyService.ApplyResult result = service.applyOutputSpec(loggedInInfo,
                List.of(malformed, passThrough, noMatch, mismatch));

        assertThat(result.reasons()).hasSize(4);
        assertThat(String.join("\n", result.reasons()))
                .doesNotContain("1111111111")
                .doesNotContain("2222222222")
                .doesNotContain("3333333333")
                .doesNotContain("4444444444");
    }
    @Test
    void shouldAppendEligibilityAlert_withoutNullOrLostExistingText() {
        for (String existing : new String[]{null, "", "Existing alert"}) {
            var patient = demographic(42, "ZZ");
            var cust = new DemographicCust();
            cust.setAlert(existing);
            var eligibility = mock(BatchEligibility.class);
            when(eligibility.getMOHResponse()).thenReturn("Expired card");
            when(eligibility.getReason()).thenReturn("Renew card");
            when(batchEligibilityDao.find(60)).thenReturn(eligibility);
            when(demographicManager.searchByHealthCard(loggedInInfo, "1234567890")).thenReturn(List.of(patient));
            when(demographicCustDao.find((Object) Integer.valueOf(42))).thenReturn(cust);
            var result = service.applyOutputSpec(loggedInInfo, List.of(record("1234567890", "ZZ", "60")));
            String message = "Invalid old version code: ZZ\nReason: Expired card- Renew card\nResponse Code: 60";
            assertThat(cust.getAlert()).isEqualTo(existing == null || existing.isEmpty() ? message : existing + "\n" + message);
            assertThat(result.appliedCount()).isEqualTo(1);
            assertThat(patient.getVer()).isEqualTo("##");
        }
    }

    // #6140: the apply appended to the alert with no length check. A longer alert was cut at the
    // column (empty sql_mode: the newest message lost) or failed the whole batch (strict).

    private static final String MESSAGE = "Invalid old version code: ZZ\nReason: Expired card- Renew card\nResponse Code: 60";

    /** Characters the alert holds after `existing` once the message is appended, as the Master Record form sends it. */
    private static int formLength(String existing) {
        String alert = existing + "\r\n" + MESSAGE.replace("\n", "\r\n");
        return alert.codePointCount(0, alert.length());
    }

    private BillingObecOutputApplyService.ApplyResult applyWithAlert(DemographicCust cust, Demographic patient) {
        var eligibility = mock(BatchEligibility.class);
        when(eligibility.getMOHResponse()).thenReturn("Expired card");
        when(eligibility.getReason()).thenReturn("Renew card");
        when(batchEligibilityDao.find(60)).thenReturn(eligibility);
        when(demographicManager.searchByHealthCard(loggedInInfo, "1234567890")).thenReturn(List.of(patient));
        when(demographicCustDao.find((Object) Integer.valueOf(42))).thenReturn(cust);
        return service.applyOutputSpec(loggedInInfo, List.of(record("1234567890", "ZZ", "60")));
    }

    @Test
    void shouldWriteTheAlert_whenItFillsTheLimitExactly() {
        String existing = "A".repeat(DemographicCust.ALERT_MAX_LENGTH - formLength(""));
        assertThat(formLength(existing)).isEqualTo(DemographicCust.ALERT_MAX_LENGTH);
        var cust = new DemographicCust();
        cust.setAlert(existing);

        var result = applyWithAlert(cust, demographic(42, "ZZ"));

        assertThat(cust.getAlert()).isEqualTo(existing + "\n" + MESSAGE);
        verify(demographicCustDao).merge(cust);
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void shouldLeaveTheAlertAndSaySo_whenTheMessageWouldMakeItTooLong() {
        String existing = "A".repeat(DemographicCust.ALERT_MAX_LENGTH - formLength("") + 1);
        var cust = new DemographicCust();
        cust.setAlert(existing);
        var patient = demographic(42, "ZZ");

        var result = applyWithAlert(cust, patient);

        // Nothing is cut: the alert keeps its text, and the row's version is still flagged.
        assertThat(cust.getAlert()).isEqualTo(existing);
        verify(demographicCustDao, never()).merge(any(DemographicCust.class));
        assertThat(patient.getVer()).isEqualTo("##");
        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(result.reasons()).singleElement().asString()
                .startsWith("Alert not updated for row 1:")
                .contains(String.valueOf(DemographicCust.ALERT_MAX_LENGTH))
                .doesNotContain("1234567890");
    }

    @Test
    void shouldCountAnEarlierBareLineFeedAsTheFormWillSendIt_whenTheAlertHoldsOne() {
        // An earlier apply left a bare LF in the alert. Stored, it is one character and the alert would
        // fit; the Master Record form sends it back as CR LF, so it is one over and stays unwritten.
        String existing = "A".repeat(100) + "\n" + "A".repeat(DemographicCust.ALERT_MAX_LENGTH - formLength("") - 101);
        String stored = existing + "\n" + MESSAGE;
        assertThat(stored.length()).isLessThanOrEqualTo(DemographicCust.ALERT_MAX_LENGTH);
        assertThat(BillingObecOutputApplyService.asFormSubmits(stored).length())
                .isEqualTo(DemographicCust.ALERT_MAX_LENGTH + 1);
        var cust = new DemographicCust();
        cust.setAlert(existing);

        var result = applyWithAlert(cust, demographic(42, "ZZ"));

        assertThat(cust.getAlert()).isEqualTo(existing);
        verify(demographicCustDao, never()).merge(any(DemographicCust.class));
        assertThat(result.reasons()).singleElement().asString().startsWith("Alert not updated for row 1:");
    }

    @Test
    void shouldCountTheFormsOwnCrLfOnce_whenTheAlertWasSavedFromTheForm() {
        // A form save stored CR LF. It counts as the two characters it is, not four: the alert fills the
        // limit exactly and is written.
        String existing = "A".repeat(100) + "\r\n" + "A".repeat(DemographicCust.ALERT_MAX_LENGTH - formLength("") - 102);
        String stored = existing + "\n" + MESSAGE;
        assertThat(BillingObecOutputApplyService.asFormSubmits(stored).length()).isEqualTo(DemographicCust.ALERT_MAX_LENGTH);
        var cust = new DemographicCust();
        cust.setAlert(existing);

        var result = applyWithAlert(cust, demographic(42, "ZZ"));

        assertThat(cust.getAlert()).isEqualTo(stored);
        verify(demographicCustDao).merge(cust);
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void shouldCountCharactersOutsideTheBasicPlaneOnce_whenTheAlertFits() {
        // Each of these is 2 UTF-16 units but 1 character, as the column counts it.
        String existing = "\uD835\uDC00".repeat(DemographicCust.ALERT_MAX_LENGTH - formLength(""));
        var cust = new DemographicCust();
        cust.setAlert(existing);

        applyWithAlert(cust, demographic(42, "ZZ"));

        assertThat(cust.getAlert()).isEqualTo(existing + "\n" + MESSAGE);
        verify(demographicCustDao).merge(cust);
    }

    @Test
    void shouldMeasureAnAlertTheFormAlreadySaved_withItsOwnCrLf() {
        assertThat(BillingObecOutputApplyService.asFormSubmits("a\nb\r\nc")).isEqualTo("a\r\nb\r\nc");
    }

}
