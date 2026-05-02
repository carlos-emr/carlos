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
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
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

    @Test
    void shouldReturnInactiveExtProperties_whenLoadingInactiveThirdPartyBillingProperties() {
        BillingONExt row = new BillingONExt();
        row.setKeyVal("billTo");
        row.setValue("77");
        when(extDao.getInactiveBillingExtItems("42")).thenReturn(List.of(row));

        Properties properties = service.get3rdPartBillPropInactive("42");

        assertThat(properties).containsEntry("billTo", "77");
    }

    @Test
    void shouldReportKeyExistence_whenExtRowsAreFound() {
        when(extDao.findByBillingNoAndKey(42, "gst")).thenReturn(List.of(new BillingONExt()));

        assertThat(service.keyExists("42", "gst")).isTrue();
        assertThat(service.keyExists("42", "missing")).isFalse();
    }

    @Test
    void shouldUpdateAllMatchingKeyStatuses_whenRowsExist() {
        BillingONExt row1 = new BillingONExt();
        BillingONExt row2 = new BillingONExt();
        when(extDao.findByBillingNoAndKey(42, "billTo")).thenReturn(List.of(row1, row2));

        assertThat(service.updateKeyStatus("42", "billTo", BillingThirdPartyService.INACTIVE)).isTrue();

        assertThat(row1.getStatus()).isEqualTo('0');
        assertThat(row2.getStatus()).isEqualTo('0');
        verify(extDao).merge(same(row1));
        verify(extDao).merge(same(row2));
    }

    @Test
    void shouldUpdateValueAndReactivateKey_whenRowsExist() {
        BillingONExt row = new BillingONExt();
        row.setStatus('0');
        when(extDao.findByBillingNoAndKey(42, "payment")).thenReturn(List.of(row));

        assertThat(service.updateKeyValue("42", "payment", "15.00")).isTrue();

        assertThat(row.getValue()).isEqualTo("15.00");
        assertThat(row.getStatus()).isEqualTo('1');
        verify(extDao).merge(same(row));
    }

    @Test
    void shouldReturnLocalClinicAddress_whenClinicExists() {
        Clinic clinic = new Clinic();
        clinic.setClinicName("Clinic");
        clinic.setClinicAddress("1 Main");
        clinic.setClinicCity("Hamilton");
        clinic.setClinicProvince("ON");
        clinic.setClinicPostal("A1A1A1");
        clinic.setClinicPhone("555-1212");
        clinic.setClinicFax("555-1313");
        when(clinicDao.getClinic()).thenReturn(clinic);

        Properties address = service.getLocalClinicAddr();

        assertThat(address)
                .containsEntry("clinic_name", "Clinic")
                .containsEntry("clinic_address", "1 Main")
                .containsEntry("clinic_city", "Hamilton")
                .containsEntry("clinic_province", "ON")
                .containsEntry("clinic_postal", "A1A1A1")
                .containsEntry("clinic_phone", "555-1212")
                .containsEntry("clinic_fax", "555-1313");
    }

    @Test
    void shouldReturnPaymentTypeMap_whenPaymentTypesExist() {
        BillingPaymentType cheque = paymentType(1, "Cheque");
        BillingPaymentType cash = paymentType(2, "Cash");
        when(typeDao.findAll()).thenReturn(List.of(cheque, cash));

        Properties methods = service.get3rdPayMethod();

        assertThat(methods)
                .containsEntry("1", "Cheque")
                .containsEntry("2", "Cash");
    }

    @Test
    void shouldReturnGstTotal_whenExtDaoReturnsValue() {
        when(extDao.getAccountVal(42, "gst")).thenReturn(new BigDecimal("1.23"));

        Properties gst = service.getGstTotal("42");

        assertThat(gst).containsEntry("gst", "1.23");
    }

    @Test
    void shouldReturnEmptyAddressProperties_whenAddressIsMissing() {
        when(addressDao.find(77)).thenReturn(null);

        assertThat(service.get3rdAddr("77")).isEmpty();
        verify(addressDao).find(77);
    }

    @Test
    void shouldPersistThirdPartyAddress_whenAddingAddressRecord() {
        Properties val = new Properties();
        val.setProperty("attention", "AP");
        val.setProperty("company_name", "Company");
        val.setProperty("address", "1 Main");
        val.setProperty("city", "Hamilton");
        val.setProperty("province", "ON");
        val.setProperty("postcode", "A1A1A1");
        val.setProperty("telephone", "555-1212");
        val.setProperty("fax", "555-1313");
        org.mockito.Mockito.doAnswer(invocation -> {
            Billing3rdPartyAddress address = invocation.getArgument(0);
            java.lang.reflect.Field idField = Billing3rdPartyAddress.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(address, 123);
            return null;
        }).when(addressDao).persist(any(Billing3rdPartyAddress.class));

        int id = service.addOne3rdAddrRecord(val);

        assertThat(id).isEqualTo(123);
        ArgumentCaptor<Billing3rdPartyAddress> captor = ArgumentCaptor.forClass(Billing3rdPartyAddress.class);
        verify(addressDao).persist(captor.capture());
        assertThat(captor.getValue().getCompanyName()).isEqualTo("Company");
        assertThat(captor.getValue().getPostalCode()).isEqualTo("A1A1A1");
    }

    private static BillingPaymentType paymentType(int id, String type) {
        BillingPaymentType paymentType = new BillingPaymentType();
        paymentType.setId(id);
        paymentType.setPaymentType(type);
        return paymentType;
    }
}
