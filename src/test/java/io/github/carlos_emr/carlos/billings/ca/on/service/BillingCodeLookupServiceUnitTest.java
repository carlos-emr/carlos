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

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingCodeLookupService} search, edit, and insert behavior. */
@DisplayName("ON BillingCodeLookupService")
@Tag("unit")
@Tag("billing")
class BillingCodeLookupServiceUnitTest {

    @Test
    void shouldBeSpringTransactionalService_forWriteMethods() {
        assertThat(BillingCodeLookupService.class.getAnnotation(Service.class)).isNotNull();
        assertThat(BillingCodeLookupService.class.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void shouldUseInjectedDao_whenSearchingMostRecentBillingCode() {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        BillingService billingService = new BillingService();
        billingService.setServiceCode("A001A");
        billingService.setDescription("Minor assessment");
        billingService.setValue("33.70");
        billingService.setBillingserviceDate(new Date(0));
        when(billingServiceDao.findMostRecentByServiceCode("A001A")).thenReturn(List.of(billingService));

        BillingCodeLookupService billingCodeLookupService = new BillingCodeLookupService(billingServiceDao);

        assertThat(billingCodeLookupService.searchBillingCode("A001A"))
                .containsEntry("service_code", "A001A")
                .containsEntry("description", "Minor assessment")
                .containsEntry("value", "33.70")
                .containsEntry("count", "1");
        verify(billingServiceDao).findMostRecentByServiceCode("A001A");
    }

    @Test
    void shouldUseInjectedDao_whenInsertingBillingCode() throws Exception {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        BillingCodeLookupService billingCodeLookupService = new BillingCodeLookupService(billingServiceDao);

        assertThat(billingCodeLookupService.insertBillingCode("33.70", "A001A",
                "2026-04-28", "Minor assessment", "9999-12-31")).isTrue();

        verify(billingServiceDao).persist(any(BillingService.class));
    }
}
