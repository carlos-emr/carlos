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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AllergyTransfer;

/**
 * SOAP-level endpoint tests for {@link AllergyWs} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-WS pipeline for allergy operations:
 * SOAP envelope marshalling/unmarshalling, WSDL processing, and response
 * serialization of {@link AllergyTransfer} arrays.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("AllergyWs SOAP endpoint tests")
class AllergyWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private AllergyManager allergyManager;

    private AllergyWs ws;

    @Override
    protected Object getServiceBean() {
        ws = new AllergyWs();
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return AllergyWs.class;
    }

    @BeforeEach
    void setUpMocks() {
        registerMock(AllergyManager.class, allergyManager);
        injectDependency(ws, "allergyManager", allergyManager);
    }

    /** Tests for the getAllergy SOAP operation. */
    @Nested
    @DisplayName("getAllergy operation")
    class GetAllergy {

        @Test
        @DisplayName("should return allergy transfer when valid ID provided")
        void shouldReturnAllergyTransfer_whenValidIdProvided() {
            Allergy allergy = new Allergy();
            allergy.setId(42);
            allergy.setDescription("Penicillin");
            when(allergyManager.getAllergy(any(LoggedInInfo.class), eq(42))).thenReturn(allergy);

            AllergyWs proxy = createClient(AllergyWs.class);
            AllergyTransfer result = proxy.getAllergy(42);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when allergy not found")
        void shouldReturnNull_whenAllergyNotFound() {
            when(allergyManager.getAllergy(any(LoggedInInfo.class), eq(999))).thenReturn(null);

            AllergyWs proxy = createClient(AllergyWs.class);
            AllergyTransfer result = proxy.getAllergy(999);

            assertThat(result).isNull();
        }
    }

    /** Tests for the getAllergiesUpdatedAfterDate SOAP operation. */
    @Nested
    @DisplayName("getAllergiesUpdatedAfterDate operation")
    class GetAllergiesUpdatedAfterDate {

        @Test
        @DisplayName("should return allergy array when results exist")
        void shouldReturnAllergyArray_whenResultsExist() {
            List<Allergy> allergies = new ArrayList<>();
            Allergy a1 = new Allergy();
            a1.setId(1);
            a1.setDescription("Peanut");
            allergies.add(a1);
            when(allergyManager.getUpdatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(allergies);

            AllergyWs proxy = createClient(AllergyWs.class);
            AllergyTransfer[] result = proxy.getAllergiesUpdatedAfterDate(new Date(), 10);

            assertThat(result).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should return empty array when no results")
        void shouldReturnEmptyArray_whenNoResults() {
            when(allergyManager.getUpdatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(new ArrayList<>());

            AllergyWs proxy = createClient(AllergyWs.class);
            AllergyTransfer[] result = proxy.getAllergiesUpdatedAfterDate(new Date(), 10);

            assertThat(result).isNotNull().isEmpty();
        }
    }

    /** Tests for the getAllergiesByDemographicIdAfter SOAP operation. */
    @Nested
    @DisplayName("getAllergiesByDemographicIdAfter operation")
    class GetAllergiesByDemographicIdAfter {

        @Test
        @DisplayName("should return allergies for demographic after date")
        void shouldReturnAllergies_forDemographicAfterDate() {
            List<Allergy> allergies = new ArrayList<>();
            Allergy a = new Allergy();
            a.setId(10);
            allergies.add(a);
            when(allergyManager.getByDemographicIdUpdatedAfterDate(any(LoggedInInfo.class), eq(100), any(Date.class)))
                .thenReturn(allergies);

            AllergyWs proxy = createClient(AllergyWs.class);
            Calendar cal = Calendar.getInstance();
            AllergyTransfer[] result = proxy.getAllergiesByDemographicIdAfter(cal, 100);

            assertThat(result).isNotNull().isNotEmpty();
        }
    }
}
