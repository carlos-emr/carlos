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

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit coverage for RA summary totals and strict amount parsing. */
@DisplayName("GenerateRaSummaryViewModelAssembler")
@Tag("unit")
@Tag("billing")
class GenerateRaSummaryViewModelAssemblerUnitTest {

    @Test
    void shouldRejectMalformedInvoicedAmount_insteadOfSilentlyZeroingTotal() {
        RaHeaderDao raHeaderDao = mock(RaHeaderDao.class);
        RaDetailDao raDetailDao = mock(RaDetailDao.class);
        ProviderDao providerDao = mock(ProviderDao.class);
        BillingDao billingDao = mock(BillingDao.class);

        RaHeader header = new RaHeader();
        header.setStatus("A");
        when(raHeaderDao.find((Object) 7)).thenReturn(header);
        when(raDetailDao.search_raprovider(7)).thenReturn(Collections.emptyList());
        when(raDetailDao.search_raob(7)).thenReturn(Collections.emptyList());
        when(raDetailDao.search_racolposcopy(7)).thenReturn(Collections.emptyList());
        when(providerDao.getActiveProviders()).thenReturn(Collections.emptyList());

        RaDetail detail = new RaDetail();
        detail.setBillingNo(100);
        detail.setAmountClaim("not-money");
        detail.setAmountPay("12.34");
        detail.setServiceDate("20260428");
        detail.setServiceCode("A001");
        when(raDetailDao.search_rasummary_dt(7, "%")).thenReturn(List.of(detail));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("rano", "7");

        GenerateRaSummaryViewModelAssembler assembler =
                new GenerateRaSummaryViewModelAssembler(raHeaderDao, raDetailDao, providerDao, billingDao);

        assertThatThrownBy(() -> assembler.assemble(request, null))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("invoicedAmount")
                .hasMessageContaining("malformed");
    }
}
