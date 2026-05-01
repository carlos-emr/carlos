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

import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitCommand;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitItemCommand;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingCorrectionSubmissionService")
@Tag("unit")
@Tag("billing")
class BillingCorrectionSubmissionServiceUnitTest extends CarlosUnitTestBase {

    @Mock private BillingDetailDao billingDetailDao;
    @Mock private RecycleBinDao recycleBinDao;
    @Mock private BillingDao billingDao;
    @Mock private LoggedInInfo loggedInInfo;

    private AutoCloseable mockitoCloseable;
    private BillingCorrectionSubmissionService service;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        service = new BillingCorrectionSubmissionService(billingDetailDao, recycleBinDao, billingDao);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldPersistCorrectionFromTypedCommandWithoutLegacyBeans() {
        Billing existing = new Billing();
        BillingDetail oldDetail = new BillingDetail();
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(billingDetailDao.findAllIncludingDeletedByBillingNo(42)).thenReturn(List.of(oldDetail));
        when(billingDao.find(42)).thenReturn(existing);

        BillingCorrectionSubmitCommand command = new BillingCorrectionSubmitCommand(
                "42",
                "<rd>Ref Doctor</rd>",
                "3000",
                "1234567890",
                "1980-01-01",
                "00",
                "2026-04-28",
                "O",
                "0000",
                "999998",
                "2026-04-28",
                List.of(new BillingCorrectionSubmitItemCommand(
                        "A001A", "Minor assessment", "2000", "250", "2")));

        service.submit(loggedInInfo, command);

        ArgumentCaptor<RecycleBin> recycleBin = ArgumentCaptor.forClass(RecycleBin.class);
        verify(recycleBinDao).persist(recycleBin.capture());
        assertThat(recycleBin.getValue().getTableContent()).isEqualTo("<rd>Ref Doctor</rd>");

        assertThat(oldDetail.getStatus()).isEqualTo("D");
        verify(billingDetailDao).merge(oldDetail);

        assertThat(existing.getTotal()).isEqualTo("30.00");
        assertThat(existing.getHin()).isEqualTo("1234567890");
        verify(billingDao).merge(existing);

        ArgumentCaptor<BillingDetail> detail = ArgumentCaptor.forClass(BillingDetail.class);
        verify(billingDetailDao).persist(detail.capture());
        assertThat(detail.getValue().getBillingNo()).isEqualTo(42);
        assertThat(detail.getValue().getServiceCode()).isEqualTo("A001A");
        assertThat(detail.getValue().getServiceDesc()).isEqualTo("Minor assessment");
        assertThat(detail.getValue().getBillingAmount()).isEqualTo("2000");
        assertThat(detail.getValue().getDiagnosticCode()).isEqualTo("250");
        assertThat(detail.getValue().getBillingUnit()).isEqualTo("2");
    }
}
