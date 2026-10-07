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
package io.github.carlos_emr.carlos.billings.ca.bc.data;

import java.util.List;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillRecipientsDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingHistoryDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingHistory;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.SqlUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The BC billing history archive that every reprocess save writes (#4343).
 *
 * <p>The current-state lookup used a JPA-style {@code ?1} with no value through plain JDBC, so it
 * never found the bill and every archive, and with it every save from the adjust bill page and the
 * Bill Status mass edit, failed with "Archive Not Created".</p>
 *
 * @since 2026-10-07
 */
@DisplayName("BillingHistoryDAO (BC billing history archive)")
@Tag("unit")
@Tag("dao")
@Tag("create")
@Tag("billing")
class BillingHistoryDAOUnitTest extends CarlosUnitTestBase {

    private AutoCloseable mockitoCloseable;
    private MockedStatic<SqlUtils> sqlUtilsMock;

    @Mock private BillingHistoryDao mockBillingHistoryDao;
    @Mock private BillRecipientsDao mockBillRecipientsDao;
    @Mock private BillingDao mockBillingDao;
    @Mock private BillingmasterDAO mockBillingmasterDao;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(BillingHistoryDao.class, mockBillingHistoryDao);
        // The lookup builds an MSPReconcile, whose fields fetch these.
        registerMock(BillRecipientsDao.class, mockBillRecipientsDao);
        registerMock(BillingDao.class, mockBillingDao);
        registerMock(BillingmasterDAO.class, mockBillingmasterDao);
        sqlUtilsMock = mockStatic(SqlUtils.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sqlUtilsMock != null) sqlUtilsMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    @DisplayName("should look the bill up by a bound ? parameter and archive its current state")
    void shouldArchiveCurrentState_byBoundBillingmasterNo() {
        // A private bill skips the Teleplan sequence-number lookup, which is not under test here.
        sqlUtilsMock.when(() -> SqlUtils.getQueryResultsList(anyString(), ArgumentMatchers.<Object>any()))
                .thenReturn(List.<String[]>of(new String[]{"999998", "Pri", "O", "27.90", "0"}));

        new BillingHistoryDAO().createBillingHistoryArchive("41");

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        sqlUtilsMock.verify(() -> SqlUtils.getQueryResultsList(query.capture(), eq("41")));
        assertThat(query.getValue()).endsWith("bm.billingmaster_no = ?").doesNotContain("?1");
        ArgumentCaptor<BillingHistory> archived = ArgumentCaptor.forClass(BillingHistory.class);
        verify(mockBillingHistoryDao).persist(archived.capture());
        assertThat(archived.getValue().getBillingMasterNo()).isEqualTo(41);
        assertThat(archived.getValue().getStatus()).isEqualTo("O");
        assertThat(archived.getValue().getAmount()).isEqualTo("27.9");
    }

    @Test
    @DisplayName("should archive a received payment with its amount and payment type")
    void shouldArchivePayment_withAmountAndPaymentType() {
        // The payment form (ReceivePayment2Action) used to find no bill here and archive nothing.
        sqlUtilsMock.when(() -> SqlUtils.getQueryResultsList(anyString(), ArgumentMatchers.<Object>any()))
                .thenReturn(List.<String[]>of(new String[]{"999998", "Pri", "P", "27.90", "0"}));

        new BillingHistoryDAO().createBillingHistoryArchive("41", 12.5, "3");

        ArgumentCaptor<BillingHistory> archived = ArgumentCaptor.forClass(BillingHistory.class);
        verify(mockBillingHistoryDao).persist(archived.capture());
        assertThat(archived.getValue().getBillingMasterNo()).isEqualTo(41);
        assertThat(archived.getValue().getAmountReceived()).isEqualTo("12.5");
        assertThat(archived.getValue().getPaymentTypeId()).isEqualTo(3);
    }

    @Test
    @DisplayName("should still refuse to archive a bill that does not exist")
    void shouldThrow_whenBillDoesNotExist() {
        sqlUtilsMock.when(() -> SqlUtils.getQueryResultsList(anyString(), ArgumentMatchers.<Object>any()))
                .thenReturn(null);

        assertThatThrownBy(() -> new BillingHistoryDAO().createBillingHistoryArchive("41"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Archive Not Created");
        verify(mockBillingHistoryDao, never()).persist(ArgumentMatchers.any());
    }
}
