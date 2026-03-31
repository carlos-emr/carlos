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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.DrugProduct;
import io.github.carlos_emr.carlos.managers.DrugDispensingManager;
import io.github.carlos_emr.carlos.managers.DrugProductManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugProductResponse;

/**
 * HTTP-level endpoint tests for {@link ProductDispensingService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for drug product dispensing REST endpoints. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ProductDispensingService REST endpoint tests")
class ProductDispensingServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private DrugDispensingManager mockDrugDispensingManager;

    @Mock
    private DrugProductManager mockDrugProductManager;

    @Override
    protected Object getServiceBean() {
        ProductDispensingService service = new ProductDispensingService();
        injectDependency(service, "drugDispensingManager", mockDrugDispensingManager);
        injectDependency(service, "drugProductManager", mockDrugProductManager);
        return service;
    }

    private DrugProduct createTestDrugProduct(Integer id, String name, String code) {
        DrugProduct product = new DrugProduct();
        product.setId(id);
        product.setName(name);
        product.setCode(code);
        product.setAmount(100);
        product.setExpiryDate(new Date());
        return product;
    }

    /** Tests for GET /productDispensing/drugProducts endpoint. */
    @Nested
    @DisplayName("GET /productDispensing/drugProducts")
    class GetAllDrugProducts {

        @Test
        @DisplayName("should return 200 with drug products list")
        void shouldReturn200WithProducts_whenProductsExist() {
            DrugProduct product = createTestDrugProduct(1, "Acetaminophen", "ACE001");
            when(mockDrugProductManager.getAllDrugProductsByNameAndLotCount(
                any(LoggedInInfo.class), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(1);
            when(mockDrugProductManager.getAllDrugProductsByNameAndLot(
                any(LoggedInInfo.class), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(List.of(product));

            Response response = request().path("/productDispensing/drugProducts").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no products")
        void shouldReturn200WithEmptyList_whenNoProducts() {
            when(mockDrugProductManager.getAllDrugProductsByNameAndLotCount(
                any(LoggedInInfo.class), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(0);
            when(mockDrugProductManager.getAllDrugProductsByNameAndLot(
                any(LoggedInInfo.class), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/productDispensing/drugProducts").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    /** Tests for GET /productDispensing/drugProduct/{id} endpoint. */
    @Nested
    @DisplayName("GET /productDispensing/drugProduct/{id}")
    class GetDrugProduct {

        @Test
        @DisplayName("should return 200 with product details when valid ID")
        void shouldReturn200WithProduct_whenValidIdProvided() {
            DrugProduct product = createTestDrugProduct(42, "Ibuprofen", "IBU001");
            when(mockDrugProductManager.getDrugProduct(any(LoggedInInfo.class), eq(42)))
                .thenReturn(product);

            Response response = request().path("/productDispensing/drugProduct/42").get();

            assertThat(response.getStatus()).isEqualTo(200);
            DrugProductResponse body = response.readEntity(DrugProductResponse.class);
            assertThat(body.getContent()).hasSize(1);
        }
    }

    /** Tests for GET /productDispensing/drugProducts/uniqueNames endpoint. */
    @Nested
    @DisplayName("GET /productDispensing/drugProducts/uniqueNames")
    class GetUniqueDrugProductNames {

        @Test
        @DisplayName("should return 200 with unique product names")
        void shouldReturn200WithNames_whenProductsExist() {
            when(mockDrugProductManager.findUniqueDrugProductNames(any(LoggedInInfo.class)))
                .thenReturn(List.of("Acetaminophen", "Ibuprofen"));

            Response response = request().path("/productDispensing/drugProducts/uniqueNames").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
