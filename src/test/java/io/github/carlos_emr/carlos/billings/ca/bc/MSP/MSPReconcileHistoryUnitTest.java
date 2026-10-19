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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillRecipientsDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingHistoryDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.TeleplanS00Dao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS00;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.entities.MSPBill;
import io.github.carlos_emr.carlos.entities.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression coverage for report-local payment history batching (#3967). */
@Tag("unit")
@Tag("billing-bc")
@DisplayName("MSP reconcile report payment history")
class MSPReconcileHistoryUnitTest extends CarlosUnitTestBase {
    private BillingHistoryDao historyDao;
    private TeleplanS00Dao teleplanDao;
    private MSPReconcile reconcile;
    private ResultSet rows;
    private MockedStatic<LegacyJdbcQuery> jdbc;

    @BeforeEach
    void setUp() throws Exception {
        createAndRegisterMock(BillRecipientsDao.class);
        createAndRegisterMock(BillingDao.class);
        createAndRegisterMock(BillingmasterDAO.class);
        historyDao = createAndRegisterMock(BillingHistoryDao.class);
        teleplanDao = createAndRegisterMock(TeleplanS00Dao.class);
        reconcile = spy(new MSPReconcile());
        doReturn(new Properties()).when(reconcile).currentC12Records();
        doReturn("").when(reconcile).getS00String(anyString());
        Provider provider = new Provider();
        provider.setFirstName("Test");
        provider.setLastName("Provider");
        doReturn(provider).when(reconcile).getProvider(anyString(), anyInt());
        rows = mock(ResultSet.class);
        LegacyJdbcQuery.TrustedSql reportSql = LegacyJdbcQuery.trustedReportSelectSql("SELECT 1");
        jdbc = mockStatic(LegacyJdbcQuery.class);
        jdbc.when(() -> LegacyJdbcQuery.trustedReportSelectSql(anyString())).thenReturn(reportSql);
        jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(
                any(LegacyJdbcQuery.TrustedSql.class), any(Object[].class))).thenReturn(rows);
    }

    @AfterEach
    void closeJdbcMock() {
        jdbc.close();
    }

    private void reportRows(String... types) throws Exception {
        int[] index = {-1};
        when(rows.next()).thenAnswer(call -> ++index[0] < types.length);
        when(rows.getString(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
            case "billingmaster_no", "billing_no" -> String.valueOf(41 + index[0]);
            case "b.billingtype" -> types[index[0]];
            case "billingstatus" -> "O";
            case "bill_amount" -> "100.00";
            case "b.provider_no", "apptProvider_no" -> "12345";
            default -> "";
        });
    }

    private MSPReconcile.BillSearch report(String type) {
        return reconcile.getBillsByType("all", "all", "all", "", "", false, false, false, false, type);
    }

    @Test
    void shouldBatchTotalsAndKeepOrder_forInvoiceReport() throws Exception {
        reportRows("Pri", "MSP", "Pri");
        TeleplanS00 payment = new TeleplanS00();
        payment.setPaidAmount("002500");
        when(teleplanDao.findByOfficeNumber("0000042")).thenReturn(List.of(payment));
        when(historyDao.getTotalPaidFromHistoryByBillingMasterNos(Set.of(41, 42, 43)))
                .thenAnswer(call -> {
                    verify(rows).close();
                    return Map.of(41, 30.0, 42, 5.0);
                });

        MSPReconcile.BillSearch result = report(MSPReconcile.REP_INVOICE);

        assertThat(result.count).isEqualTo(3);
        assertThat(result.justBillingMaster).containsExactly("41", "42", "43");
        assertThat(result.list).extracting(b -> ((MSPBill) b).amtOwing)
                .containsExactly("70.0", "70.0", "100.0");
        verify(historyDao).getTotalPaidFromHistoryByBillingMasterNos(Set.of(41, 42, 43));
        verify(historyDao, never()).getTotalPaidFromHistory(anyInt(), anyBoolean());
        verify(rows).close();
    }

    @Test
    void shouldExcludeBalancedBillsAndKeepNegativePayments_forAccountsReceivable() throws Exception {
        reportRows("Pri", "Pri", "Pri");
        when(historyDao.getTotalPaidFromHistoryByBillingMasterNos(anyCollection()))
                .thenReturn(Map.of(41, 100.0, 42, -12.345));

        MSPReconcile.BillSearch result = report(MSPReconcile.REP_ACCOUNT_REC);

        assertThat(result.count).isEqualTo(2);
        assertThat(result.justBillingMaster).containsExactly("42", "43");
        assertThat(result.list).extracting(b -> ((MSPBill) b).amtOwing)
                .containsExactly("87.66", "100.0");
        verify(historyDao, never()).getTotalPaidFromHistory(anyInt(), anyBoolean());
    }

    @Test
    void shouldReloadTotals_forEachReportRequest() throws Exception {
        when(historyDao.getTotalPaidFromHistoryByBillingMasterNos(anyCollection()))
                .thenReturn(Map.of(41, 10.0), Map.of(41, 40.0));
        reportRows("Pri");
        assertThat(((MSPBill) report(MSPReconcile.REP_INVOICE).list.getFirst()).amtOwing).isEqualTo("90.0");
        reportRows("Pri");
        assertThat(((MSPBill) report(MSPReconcile.REP_INVOICE).list.getFirst()).amtOwing).isEqualTo("60.0");
        verify(historyDao, times(2)).getTotalPaidFromHistoryByBillingMasterNos(Set.of(41));
    }

    @Test
    void shouldUseOneHistoryLookup_forLargeReport() throws Exception {
        String[] types = new String[2000];
        Arrays.fill(types, "Pri");
        reportRows(types);
        when(historyDao.getTotalPaidFromHistoryByBillingMasterNos(anyCollection())).thenReturn(Map.of());

        assertThat(report(MSPReconcile.REP_INVOICE).count).isEqualTo(2000);

        verify(historyDao).getTotalPaidFromHistoryByBillingMasterNos(argThat(ids -> ids.size() == 2000));
        verify(historyDao, never()).getTotalPaidFromHistory(anyInt(), anyBoolean());
    }

    @Test
    void shouldKeepHsHandling_withBatchedHistoryTotal() throws Exception {
        reportRows("MSP");
        TeleplanS00 alreadyPaid = new TeleplanS00();
        alreadyPaid.setExp1("HS");
        when(teleplanDao.findByOfficeNumber("0000041")).thenReturn(List.of(alreadyPaid));
        when(historyDao.getTotalPaidFromHistoryByBillingMasterNos(Set.of(41))).thenReturn(Map.of(41, 5.0));

        assertThat(((MSPBill) report(MSPReconcile.REP_INVOICE).list.getFirst()).amtOwing).isEqualTo("-5.0");
        verify(historyDao, never()).getTotalPaidFromHistory(anyInt(), anyBoolean());
    }

    @Test
    void shouldPreserveHsAndSingleBillLookup_forStandaloneAmountOwing() {
        TeleplanS00 alreadyPaid = new TeleplanS00();
        alreadyPaid.setExp1("HS");
        when(teleplanDao.findByOfficeNumber("0000041")).thenReturn(List.of(alreadyPaid));
        when(historyDao.getTotalPaidFromHistory(41, false)).thenReturn(0.0);
        assertThat(reconcile.getAmountOwing("41", "100", "MSP")).isZero();
        verify(historyDao).getTotalPaidFromHistory(41, false);
        verify(historyDao, never()).getTotalPaidFromHistoryByBillingMasterNos(anyCollection());
    }

    @Test
    void shouldSkipHistoryQuery_forEmptyReport() throws Exception {
        reportRows();
        assertThat(report(MSPReconcile.REP_INVOICE).list).isEmpty();
        verifyNoInteractions(historyDao);
        verify(rows).close();
    }

    @Test
    void shouldSkipHistoryQuery_forWriteOffReport() throws Exception {
        reportRows("Pri");
        assertThat(report(MSPReconcile.REP_WO).count).isEqualTo(1);
        verifyNoInteractions(historyDao);
    }

    @Test
    void shouldCloseCursor_whenReadingFails() throws Exception {
        when(rows.next()).thenThrow(new SQLException("Synthetic read failure"));
        assertThat(report(MSPReconcile.REP_INVOICE).list).isEmpty();
        verify(rows).close();
        verifyNoInteractions(historyDao);
    }

    @Test
    void shouldCloseCursorBeforeEnrichment_whenTotalsFail() throws Exception {
        reportRows("Pri");
        when(historyDao.getTotalPaidFromHistoryByBillingMasterNos(anyCollection()))
                .thenThrow(new IllegalStateException("Synthetic aggregation failure"));
        assertThat(report(MSPReconcile.REP_INVOICE).list).isEmpty();
        verify(rows).close();
    }
}
