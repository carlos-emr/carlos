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

import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFavouriteDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFavourite;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for favourite-list mutation orchestration in {@link BillingOnLookupService}. */
@DisplayName("BillingOnLookupService")
@Tag("unit")
@Tag("billing")
class BillingOnLookupServiceUnitTest {

    private BillingONFavouriteDao favouriteDao;
    private BillingOnLookupService service;

    @BeforeEach
    void setUp() {
        favouriteDao = mock(BillingONFavouriteDao.class);
        service = new BillingOnLookupService(
                mock(OscarAppointmentDao.class),
                mock(ProfessionalSpecialistDao.class),
                mock(ClinicLocationDao.class),
                mock(ProviderDao.class),
                mock(BillingPaymentTypeDao.class),
                favouriteDao,
                mock(DemographicManager.class),
                mock(BillingONFilenameDao.class),
                mock(ProviderSiteDao.class));
    }

    @Test
    void shouldPersistFavouriteAndReturnMutationState_whenSavingAdd() {
        org.mockito.Mockito.doAnswer(invocation -> {
            BillingONFavourite favourite = invocation.getArgument(0);
            favourite.setId(42);
            return null;
        }).when(favouriteDao).persist(any(BillingONFavourite.class));

        BillingOnLookupService.FavouriteMutationRequest request =
                new BillingOnLookupService.FavouriteMutationRequest(
                        "Save",
                        "addMorning",
                        "Morning",
                        "999998",
                        Map.of("serviceCode0", "A001A",
                                "serviceUnit0", "2",
                                "serviceAt0", "1",
                                "dx", "123"));

        BillingOnLookupService.FavouriteMutationResult result = service.saveOrDeleteFavourite(request);

        assertThat(result.action()).isEqualTo("search");
        assertThat(result.messageKey()).isEqualTo("billing.billingOnFavourite.msgAdded");
        assertThat(result.messageName()).isEqualTo("Morning");
        assertThat(result.formFields()).containsEntry("name", "Morning");

        ArgumentCaptor<BillingONFavourite> captor = ArgumentCaptor.forClass(BillingONFavourite.class);
        verify(favouriteDao).persist(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Morning");
        assertThat(captor.getValue().getProviderNo()).isEqualTo("999998");
        assertThat(captor.getValue().getServiceDx()).isEqualTo("A001A|2|1|123|");
        assertThat(captor.getValue().getDeleted()).isZero();
    }

    @Test
    void shouldSoftDeleteFavouriteAndReturnMutationState_whenDeleting() {
        BillingONFavourite favourite = new BillingONFavourite();
        when(favouriteDao.findByNameAndProviderNo("Morning", "999998")).thenReturn(List.of(favourite));

        BillingOnLookupService.FavouriteMutationRequest request =
                new BillingOnLookupService.FavouriteMutationRequest(
                        "Search",
                        "Delete",
                        "Morning",
                        "999998",
                        Map.of());

        BillingOnLookupService.FavouriteMutationResult result = service.saveOrDeleteFavourite(request);

        assertThat(result.action()).isEqualTo("search");
        assertThat(result.messageKey()).isEqualTo("billing.billingOnFavourite.msgDeleted");
        assertThat(result.messageName()).isEqualTo("Morning");
        assertThat(result.formFields()).containsEntry("name", "Morning");
        assertThat(favourite.getDeleted()).isEqualTo(1);
        verify(favouriteDao).merge(favourite);
    }
}
