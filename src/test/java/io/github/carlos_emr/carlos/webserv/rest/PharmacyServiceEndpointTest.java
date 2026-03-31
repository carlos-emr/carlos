/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;

/**
 * HTTP-level endpoint tests for {@link PharmacyService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("PharmacyService REST endpoint tests")
class PharmacyServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private PharmacyInfoDao mockPharmacyInfoDao;

    @Override
    protected Object getServiceBean() {
        PharmacyService service = new PharmacyService();
        injectDependency(service, "pharmacyInfoDao", mockPharmacyInfoDao);
        return service;
    }

    @Nested
    @DisplayName("GET /pharmacies/")
    class GetPharmacies {

        @Test
        @DisplayName("should return 200 with pharmacy list")
        void shouldReturn200_whenPharmaciesExist() {
            PharmacyInfo pharmacy = new PharmacyInfo();
            pharmacy.setId(1);
            pharmacy.setName("Test Pharmacy");
            when(mockPharmacyInfoDao.findAll(any(), any())).thenReturn(List.of(pharmacy));

            Response response = request().path("/pharmacies/").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no pharmacies")
        void shouldReturn200WithEmptyList_whenNoPharmaciesExist() {
            when(mockPharmacyInfoDao.findAll(any(), any())).thenReturn(Collections.emptyList());

            Response response = request().path("/pharmacies/").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /pharmacies/{pharmacyId}")
    class GetPharmacy {

        @Test
        @DisplayName("should return 200 with pharmacy details")
        void shouldReturn200_whenPharmacyFound() {
            PharmacyInfo pharmacy = new PharmacyInfo();
            pharmacy.setId(1);
            pharmacy.setName("Downtown Pharmacy");
            when(mockPharmacyInfoDao.find(eq(1))).thenReturn(pharmacy);

            Response response = request().path("/pharmacies/1").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("DELETE /pharmacies/{pharmacyId}")
    class DeletePharmacy {

        @Test
        @DisplayName("should return 200 when pharmacy soft-deleted")
        void shouldReturn200_whenPharmacySoftDeleted() {
            PharmacyInfo pharmacy = new PharmacyInfo();
            pharmacy.setId(1);
            pharmacy.setName("Old Pharmacy");
            when(mockPharmacyInfoDao.find(eq(1))).thenReturn(pharmacy);
            when(mockPharmacyInfoDao.saveEntity(any(PharmacyInfo.class))).thenReturn(pharmacy);

            Response response = request().path("/pharmacies/1").delete();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
