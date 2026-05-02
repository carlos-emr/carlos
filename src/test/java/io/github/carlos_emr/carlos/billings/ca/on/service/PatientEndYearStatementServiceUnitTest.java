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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code PatientEndYearStatementService} patient resolution, aggregation, and PDF contracts. */
@DisplayName("PatientEndYearStatementService")
@Tag("unit")
@Tag("billing")
class PatientEndYearStatementServiceUnitTest {

    @Test
    void shouldLoadInvoiceItemsInOneBulkQuery_andGroupThemByInvoice() throws Exception {
        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        BillingONItemDao itemDao = mock(BillingONItemDao.class);
        PatientEndYearStatementService service = new PatientEndYearStatementService(
                headerDao, itemDao, mock(DemographicManager.class));

        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        demographic.setFirstName("Jane");
        demographic.setLastName("Doe");
        demographic.setHin("1234567890");

        BillingONCHeader1 first = header(101, "30.00", "10.00");
        BillingONCHeader1 second = header(202, "40.00", "5.00");
        Date from = new Date(0);
        Date to = new Date(1000);
        when(headerDao.findBillingsAndDemographicsByDemoIdAndDates(123, "PAT", from, to))
                .thenReturn(List.of(new Object[] {first}, new Object[] {second}));

        BillingONItem firstItem = item(101, "A001A", "10.00");
        BillingONItem secondItem = item(202, "K013A", "20.00");
        when(itemDao.findByCh1IdsExcludingDeletedAndSettled(List.of(101, 202)))
                .thenReturn(List.of(firstItem, secondItem));

        PatientEndYearStatementService.Result result =
                service.aggregateInvoices(demographic, from, to);

        assertThat(result.invoices()).hasSize(2);
        assertThat(result.invoices().get(0).services())
                .extracting("code")
                .containsExactly("A001A");
        assertThat(result.invoices().get(1).services())
                .extracting("code")
                .containsExactly("K013A");
        verify(itemDao).findByCh1IdsExcludingDeletedAndSettled(List.of(101, 202));
        verify(itemDao, never()).findByCh1Id(101);
        verify(itemDao, never()).findByCh1Id(202);
    }

    private static BillingONCHeader1 header(int id, String total, String paid) throws Exception {
        BillingONCHeader1 header = new BillingONCHeader1();
        // The entity id is generated in production. The unit test seeds it via
        // reflection so the aggregation result can key invoice rows
        // deterministically without bootstrapping JPA.
        java.lang.reflect.Field idField = header.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(header, id);
        header.setTotal(new BigDecimal(total));
        header.setPaid(new BigDecimal(paid));
        header.setBillingDate(new Date(0));
        return header;
    }

    private static BillingONItem item(int ch1Id, String serviceCode, String fee) {
        BillingONItem item = new BillingONItem();
        item.setCh1Id(ch1Id);
        item.setServiceCode(serviceCode);
        item.setFee(fee);
        return item;
    }
}
