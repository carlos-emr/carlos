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

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingSaveService")
@Tag("unit")
@Tag("billing")
class BillingSaveServiceUnitTest extends CarlosUnitTestBase {

    @Mock private BillingONClaimPersister mockPersister;
    @Mock private BillingONLookupService mockLookupService;
    @Mock private HttpServletRequest mockRequest;

    private AutoCloseable mockitoCloseable;
    private BillingSaveService service;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        service = new BillingSaveService(mockPersister, mockLookupService);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldReturnGeneratedBillingIdFromAddABillingRecord() {
        BillingClaimHeader1Data header = new BillingClaimHeader1Data();
        List<BillingItemData> items = List.of(new BillingItemData());
        ArrayList<Object> claim = new ArrayList<>();
        claim.add(header);
        claim.add(items);

        when(mockPersister.addOneClaimHeaderRecord(header)).thenReturn(1234);
        when(mockPersister.addItemRecord(items, 1234)).thenReturn(true);

        BillingSaveService.SaveResult result = service.addABillingRecord(claim);

        assertThat(result.saved()).isTrue();
        assertThat(result.billingId()).isEqualTo(1234);
        assertThat(header.getId()).isEqualTo("1234");
    }

    @Test
    void shouldReturnZeroBillingIdAndSkipItems_whenHeaderPersistFails() {
        BillingClaimHeader1Data header = new BillingClaimHeader1Data();
        List<BillingItemData> items = List.of(new BillingItemData());
        ArrayList<Object> claim = new ArrayList<>();
        claim.add(header);
        claim.add(items);

        when(mockPersister.addOneClaimHeaderRecord(header)).thenReturn(0);

        BillingSaveService.SaveResult result = service.addABillingRecord(claim);

        assertThat(result.saved()).isFalse();
        assertThat(result.billingId()).isZero();
        verify(mockPersister, never()).addItemRecord(items, 0);
    }

    @Test
    void shouldPassExplicitBillingIdToPrivateBillExtPersist() {
        ArrayList<Object> claim = new ArrayList<>();
        when(mockRequest.getParameter("submit")).thenReturn("Save");
        when(mockPersister.add3rdBillExt(anyMap(), eq(4321), same(claim))).thenReturn(true);

        boolean saved = service.addPrivateBillExtRecord(mockRequest, claim, 4321);

        assertThat(saved).isTrue();
        verify(mockPersister).add3rdBillExt(anyMap(), eq(4321), same(claim));
    }
}
