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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.AllergyResponse;

/**
 * HTTP-level endpoint tests for {@link AllergyService} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-RS pipeline: path routing,
 * JSON serialization via Jackson, query parameter binding, and HTTP
 * status codes. Dependencies are mocked — no database required.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("AllergyService REST endpoint tests")
class AllergyServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private AllergyManager mockAllergyManager;

    @Override
    protected Object getServiceBean() {
        AllergyService service = new AllergyService();
        injectDependency(service, "allergyManager", mockAllergyManager);
        return service;
    }

    private Allergy createTestAllergy(int id, String description) {
        Allergy allergy = new Allergy();
        allergy.setId(id);
        allergy.setDescription(description);
        allergy.setArchived(false);
        return allergy;
    }

    /** Tests for GET /allergies/active endpoint. */
    @Nested
    @DisplayName("GET /allergies/active")
    class GetActiveAllergies {

        @Test
        @DisplayName("should return 200 with allergies as JSON")
        void shouldReturn200WithAllergies_whenActiveAllergiesExist() {
            Allergy testAllergy = createTestAllergy(1, "Penicillin");
            when(mockAllergyManager.getActiveAllergies(any(LoggedInInfo.class), eq(123)))
                .thenReturn(List.of(testAllergy));

            Response response = client.path("/allergies/active")
                .query("demographicNo", 123)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            AllergyResponse body = response.readEntity(AllergyResponse.class);
            assertThat(body.getAllergies()).isNotNull();
        }

        @Test
        @DisplayName("should return 200 with empty list when no active allergies")
        void shouldReturn200WithEmptyList_whenNoActiveAllergies() {
            when(mockAllergyManager.getActiveAllergies(any(LoggedInInfo.class), eq(456)))
                .thenReturn(Collections.emptyList());

            Response response = client.path("/allergies/active")
                .query("demographicNo", 456)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            AllergyResponse body = response.readEntity(AllergyResponse.class);
            assertThat(body.getAllergies()).isEmpty();
        }

        @Test
        @DisplayName("should return multiple allergies in JSON response")
        void shouldReturnMultipleAllergies_whenMultipleActiveAllergiesExist() {
            List<Allergy> allergies = List.of(
                createTestAllergy(1, "Penicillin"),
                createTestAllergy(2, "Aspirin")
            );
            when(mockAllergyManager.getActiveAllergies(any(LoggedInInfo.class), eq(789)))
                .thenReturn(allergies);

            Response response = client.path("/allergies/active")
                .query("demographicNo", 789)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            AllergyResponse body = response.readEntity(AllergyResponse.class);
            assertThat(body.getAllergies()).hasSize(2);
        }
    }
}
