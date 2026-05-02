/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

import io.github.carlos_emr.carlos.billing.CA.ON.model.Billing3rdPartyAddress;
import io.github.carlos_emr.carlos.commn.dao.Billing3rdPartyAddressDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for third-party address/ext-row persistence behavior. */
@DisplayName("BillingThirdPartyService")
@Tag("unit")
@Tag("billing")
class BillingThirdPartyServiceUnitTest {

    private final ClinicDAO clinicDao = mock(ClinicDAO.class);
    private final Billing3rdPartyAddressDao addressDao = mock(Billing3rdPartyAddressDao.class);
    private final BillingONExtDao extDao = mock(BillingONExtDao.class);
    private final BillingPaymentTypeDao typeDao = mock(BillingPaymentTypeDao.class);
    private final BillingThirdPartyService service =
            new BillingThirdPartyService(clinicDao, addressDao, extDao, typeDao);

    @Test
    void shouldReturnFalseAndNotMerge_whenThirdPartyAddressRowIsMissing() {
        when(addressDao.find(77)).thenReturn(null);

        boolean updated = service.update3rdAddr("77", new Properties());

        assertThat(updated).isFalse();
        verify(addressDao, never()).merge(org.mockito.ArgumentMatchers.any(Billing3rdPartyAddress.class));
    }

    @Test
    void shouldDefaultNullNumericExtValueToZero_whenAddingThirdPartyExtRow() {
        when(extDao.isNumberKey("payment")).thenReturn(true);

        boolean added = service.add3rdBillExt("42", "123", "payment", null);

        assertThat(added).isTrue();
        ArgumentCaptor<BillingONExt> captor = ArgumentCaptor.forClass(BillingONExt.class);
        verify(extDao).persist(captor.capture());
        BillingONExt row = captor.getValue();
        assertThat(row.getBillingNo()).isEqualTo(42);
        assertThat(row.getDemographicNo()).isEqualTo(123);
        assertThat(row.getKeyVal()).isEqualTo("payment");
        assertThat(row.getValue()).isEqualTo("0.00");
        assertThat(row.getStatus()).isEqualTo('1');
    }

    @Test
    void shouldTrimExtKeysAndValues_whenLoadingThirdPartyBillingProperties() {
        BillingONExt row = new BillingONExt();
        row.setKeyVal(" payment ");
        row.setValue(" 12.00 ");
        when(extDao.getBillingExtItems("42")).thenReturn(List.of(row));

        Properties properties = service.get3rdPartBillProp("42");

        assertThat(properties).containsEntry("payment", "12.00");
    }
}
