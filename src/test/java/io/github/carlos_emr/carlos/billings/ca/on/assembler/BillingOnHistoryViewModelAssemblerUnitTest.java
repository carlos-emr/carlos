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

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimLoader;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnHistoryViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the partial-flag contract on
 * {@link BillingOnHistoryViewModelAssembler}.
 *
 * <p>Two distinct partial-flag triggers must each raise the banner so the
 * JSP can warn "data may be incomplete":
 * <ol>
 *   <li>{@code RuntimeException} during the row-loading loop (DAO outage,
 *       NPE on a malformed row, etc.) — caught at the outer boundary.</li>
 *   <li>{@code NumberFormatException} when a single PAT-status row's bill
 *       id is non-numeric — caught at the inner boundary, balance defaulted
 *       to zero, partial flag raised so the operator notices a $0 balance
 *       isn't the truth and isn't pursued for collection.</li>
 * </ol>
 *
 * @since 2026-04-30
 */
@DisplayName("BillingOnHistoryViewModelAssembler partial-flag")
@Tag("unit")
@Tag("billing")
class BillingOnHistoryViewModelAssemblerUnitTest {

    private BillingONPaymentDao paymentDao;
    private BillingONCHeader1Dao headerDao;
    private DemographicManager demographicManager;
    private SecurityInfoManager securityInfoManager;
    private BillingOnClaimLoader claimLoader;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        paymentDao = mock(BillingONPaymentDao.class);
        headerDao = mock(BillingONCHeader1Dao.class);
        demographicManager = mock(DemographicManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        claimLoader = mock(BillingOnClaimLoader.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(true);
    }

    private BillingOnHistoryViewModelAssembler newAssembler() {
        return new BillingOnHistoryViewModelAssembler(
                paymentDao, headerDao, demographicManager, securityInfoManager, claimLoader);
    }

    @Test
    void shouldRaisePartial_whenLoaderThrowsMidLoop() {
        when(claimLoader.getBillingHist(anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new RuntimeException("simulated DAO outage"));

        BillingOnHistoryViewModel vm = newAssembler().assemble(loggedInInfo, "1");

        assertThat(vm.isPartial()).isTrue();
        assertThat(vm.getRows()).isEmpty();
    }

    @Test
    void shouldNotRaisePartial_whenLoaderReturnsEmptyList() {
        when(claimLoader.getBillingHist(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(List.of());

        BillingOnHistoryViewModel vm = newAssembler().assemble(loggedInInfo, "1");

        assertThat(vm.isPartial()).isFalse();
        assertThat(vm.getRows()).isEmpty();
    }

    @Test
    void shouldRaisePartial_whenPatBillIdIsNonNumeric() {
        BillingClaimHeaderDto header = mock(BillingClaimHeaderDto.class);
        when(header.getId()).thenReturn("NOT_NUMERIC");
        when(header.payProgram()).thenReturn("PAT"); // matches 3rd-party regex
        when(header.getStatus()).thenReturn("O"); // propBillingType="Bill OHIP" — not Settled, so strBillType stays "PAT"
        when(header.billingDate()).thenReturn("2026-04-25");
        when(header.lastName()).thenReturn("Doe");
        when(header.firstName()).thenReturn("Jane");
        when(header.getTotal()).thenReturn("100.00");

        BillingClaimItemDto item = mock(BillingClaimItemDto.class);
        when(item.serviceCode()).thenReturn("A007");
        when(item.getDx()).thenReturn("250");

        when(claimLoader.getBillingHist(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(List.of(header, item));

        BillingOnHistoryViewModel vm = newAssembler().assemble(loggedInInfo, "1");

        assertThat(vm.isPartial()).isTrue();
        // Row is still emitted with balance=0 so the operator sees the row;
        // the banner clarifies that 0 may not be the real balance.
        assertThat(vm.getRows()).hasSize(1);
        assertThat(vm.getRows().get(0).balance()).isEqualTo("0");
    }

    @Test
    void shouldNotRaisePartial_whenPatBillIdIsNumeric_andHeaderMissing() {
        BillingClaimHeaderDto header = mock(BillingClaimHeaderDto.class);
        when(header.getId()).thenReturn("999"); // numeric, but headerDao returns null
        when(header.payProgram()).thenReturn("PAT"); // PAT path with numeric id
        when(header.getStatus()).thenReturn("O");
        when(header.billingDate()).thenReturn("2026-04-25");
        when(header.lastName()).thenReturn("Doe");
        when(header.firstName()).thenReturn("Jane");
        when(header.getTotal()).thenReturn("100.00");

        BillingClaimItemDto item = mock(BillingClaimItemDto.class);
        when(item.serviceCode()).thenReturn("A007");
        when(item.getDx()).thenReturn("250");

        when(claimLoader.getBillingHist(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(List.of(header, item));
        when(headerDao.find(any(Integer.class))).thenReturn(null);

        BillingOnHistoryViewModel vm = newAssembler().assemble(loggedInInfo, "1");

        // Numeric id but missing header is a "no balance computable" case,
        // not a corruption — partial stays false.
        assertThat(vm.isPartial()).isFalse();
        assertThat(vm.getRows()).hasSize(1);
    }
}
