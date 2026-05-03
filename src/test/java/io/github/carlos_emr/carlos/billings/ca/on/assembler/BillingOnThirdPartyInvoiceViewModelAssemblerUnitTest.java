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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnInvoiceTotalsService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingThirdPartyRecordService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnThirdPartyInvoiceViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for third-party invoice view-model assembly. */
@DisplayName("BillingOnThirdPartyInvoiceViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingOnThirdPartyInvoiceViewModelAssemblerUnitTest {

    private final CarlosProperties properties = CarlosProperties.getInstance();
    private String previousMultisites;
    private String previousInvoiceDueDate;
    private String previousUseDemoClinicInfoOnInvoice;
    private String previousPayee;

    @BeforeEach
    void setUp() {
        previousMultisites = properties.getProperty("multisites");
        previousInvoiceDueDate = properties.getProperty("invoice_due_date");
        previousUseDemoClinicInfoOnInvoice = properties.getProperty("useDemoClinicInfoOnInvoice");
        previousPayee = properties.getProperty("PAYEE");
        properties.setProperty("multisites", "on");
        properties.remove("invoice_due_date");
        properties.setProperty("useDemoClinicInfoOnInvoice", "false");
        properties.remove("PAYEE");
    }

    @AfterEach
    void tearDown() {
        restore("multisites", previousMultisites);
        restore("invoice_due_date", previousInvoiceDueDate);
        restore("useDemoClinicInfoOnInvoice", previousUseDemoClinicInfoOnInvoice);
        restore("PAYEE", previousPayee);
    }

    @Test
    void shouldRenderInvoice_withoutCallingUnusedPaymentTotalLoaders() {
        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        BillingONExtDao extDao = mock(BillingONExtDao.class);
        BillingONPaymentDao paymentDao = mock(BillingONPaymentDao.class);
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        ClinicDAO clinicDao = mock(ClinicDAO.class);
        DemographicDao demographicDao = mock(DemographicDao.class);
        ProviderDao providerDao = mock(ProviderDao.class);
        SiteDao siteDao = mock(SiteDao.class);
        BillingOnInvoiceTotalsService totalsService = mock(BillingOnInvoiceTotalsService.class);
        BillingThirdPartyRecordService thirdPartyRecordService = mock(BillingThirdPartyRecordService.class);

        BillingONCHeader1 header = mock(BillingONCHeader1.class);
        Date billingDate = new Date(0);
        when(header.getId()).thenReturn(42);
        when(header.getBillingDate()).thenReturn(billingDate);
        when(header.getTotal()).thenReturn(new BigDecimal("100.00"));
        when(header.getProviderNo()).thenReturn("999");
        when(header.getDemographicNo()).thenReturn(null);
        when(header.getDemographicName()).thenReturn("Patient One");
        when(header.getSex()).thenReturn("1");
        when(headerDao.find((Object) 42)).thenReturn(header);

        Provider provider = mock(Provider.class);
        when(provider.getFormattedName()).thenReturn("Provider, One");
        when(providerDao.getProvider("999")).thenReturn(provider);

        BillingONItem item = mock(BillingONItem.class);
        when(item.getId()).thenReturn(7);
        when(item.getServiceCode()).thenReturn("A001");
        when(item.getServiceDate()).thenReturn(billingDate);
        when(item.getServiceCount()).thenReturn("1");
        when(item.getDx()).thenReturn("123");
        when(item.getFee()).thenReturn("100.00");
        when(headerDao.findActiveItems(42)).thenReturn(Collections.singletonList(item));

        BillingService billingService = mock(BillingService.class);
        when(billingService.getDescription()).thenReturn("Consult");
        when(billingServiceDao.searchBillingCode("A001", "ON", billingDate)).thenReturn(billingService);

        Properties clinicProperties = new Properties();
        clinicProperties.setProperty("clinic_name", "Clinic");
        Properties thirdPartyProperties = new Properties();
        thirdPartyProperties.setProperty("payment", "10.00");
        thirdPartyProperties.setProperty("discount", "5.00");
        thirdPartyProperties.setProperty("credit", "2.00");
        Properties paymentMethodProperties = new Properties();
        when(thirdPartyRecordService.getLocalClinicAddr()).thenReturn(clinicProperties);
        when(thirdPartyRecordService.get3rdPartBillProp("42")).thenReturn(thirdPartyProperties);
        when(thirdPartyRecordService.get3rdPayMethod()).thenReturn(paymentMethodProperties);
        when(extDao.findByBillingNoAndKey(42, "payMethod")).thenReturn(Collections.emptyList());
        when(paymentDao.find3rdPartyPayRecordsByBill(header)).thenReturn(Collections.emptyList());

        BillingOnThirdPartyInvoiceViewModelAssembler assembler =
                new BillingOnThirdPartyInvoiceViewModelAssembler(
                        headerDao, extDao, paymentDao, billingServiceDao, clinicDao, demographicDao,
                        providerDao, siteDao, totalsService, thirdPartyRecordService);

        BillingOnThirdPartyInvoiceViewModel model = assembler.assemble(invoiceRequest(), null);

        assertThat(model.isInvoiceLoaded()).isTrue();
        assertThat(model.getBalanceAmount()).isEqualTo("87.00");
        assertThat(model.getInvoiceItems()).hasSize(1);
        verify(paymentDao, never()).find3rdPartyPayRecordsByBill(any(BillingONCHeader1.class));
        verify(totalsService, never()).calculateBalanceOwing(anyInt());
    }

    @Test
    void shouldExposeInvoiceParseError_whenBillingNoIsMalformed() {
        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        BillingONExtDao extDao = mock(BillingONExtDao.class);
        BillingONPaymentDao paymentDao = mock(BillingONPaymentDao.class);
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        ClinicDAO clinicDao = mock(ClinicDAO.class);
        DemographicDao demographicDao = mock(DemographicDao.class);
        ProviderDao providerDao = mock(ProviderDao.class);
        SiteDao siteDao = mock(SiteDao.class);
        BillingOnInvoiceTotalsService totalsService = mock(BillingOnInvoiceTotalsService.class);
        BillingThirdPartyRecordService thirdPartyRecordService = mock(BillingThirdPartyRecordService.class);
        when(thirdPartyRecordService.getLocalClinicAddr()).thenReturn(new Properties());
        when(thirdPartyRecordService.get3rdPartBillProp("")).thenReturn(new Properties());
        when(thirdPartyRecordService.get3rdPayMethod()).thenReturn(new Properties());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getLocale()).thenReturn(Locale.CANADA);
        when(request.getParameter("billingNo")).thenReturn("not-a-number");

        BillingOnThirdPartyInvoiceViewModelAssembler assembler =
                new BillingOnThirdPartyInvoiceViewModelAssembler(
                        headerDao, extDao, paymentDao, billingServiceDao, clinicDao, demographicDao,
                        providerDao, siteDao, totalsService, thirdPartyRecordService);

        BillingOnThirdPartyInvoiceViewModel model = assembler.assemble(request, null);

        assertThat(model.isInvoiceLoaded()).isFalse();
        assertThat(model.isInvoiceParseError()).isTrue();
        verify(headerDao, never()).find(any());
    }

    private static HttpServletRequest invoiceRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getLocale()).thenReturn(Locale.CANADA);
        when(request.getParameter("billingNo")).thenReturn("42");
        return request;
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, previousValue);
        }
    }
}
