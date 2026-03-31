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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.AppDefinitionDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao.EFormSortOrder;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EFormTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EncounterFormTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.FormListTo1;

/**
 * HTTP-level endpoint tests for {@link FormsService} using CXF local transport.
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
@DisplayName("FormsService REST endpoint tests")
class FormsServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private FormsManager mockFormsManager;

    @Mock
    private AppDefinitionDao mockAppDefinitionDao;

    @Override
    protected Object getServiceBean() {
        FormsService service = new FormsService();
        injectDependency(service, "formsManager", mockFormsManager);
        injectDependency(service, "appDefinitionDao", mockAppDefinitionDao);
        return service;
    }

    /** Tests for GET /forms/{demographicNo}/all endpoint. */
    @Nested
    @DisplayName("GET /forms/{demographicNo}/all")
    class GetFormsForHeading {

        @Test
        @DisplayName("should return 200 with completed eforms")
        void shouldReturn200WithCompletedEForms_whenHeadingIsCompleted() {
            EFormData eformData = new EFormData();
            eformData.setId(1);
            eformData.setFormId(10);
            eformData.setFormName("Blood Pressure Form");
            eformData.setSubject("BP Check");

            when(mockFormsManager.findByDemographicId(any(LoggedInInfo.class), eq(123)))
                .thenReturn(new ArrayList<>(List.of(eformData)));

            Response response = request().path("/forms/123/all")
                .query("heading", "Completed")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            FormListTo1 body = response.readEntity(FormListTo1.class);
            assertThat(body).isNotNull();
        }

        @Test
        @DisplayName("should return 200 with available eforms when heading is not Completed")
        void shouldReturn200WithAvailableEForms_whenHeadingIsNotCompleted() {
            EForm eform = new EForm();
            eform.setFormName("Referral Form");
            eform.setSubject("Specialist Referral");

            when(mockFormsManager.findByStatus(any(LoggedInInfo.class), eq(true), isNull()))
                .thenReturn(new ArrayList<>(List.of(eform)));

            Response response = request().path("/forms/123/all")
                .query("heading", "Available")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            FormListTo1 body = response.readEntity(FormListTo1.class);
            assertThat(body).isNotNull();
        }
    }

    /** Tests for GET /forms/allEForms endpoint. */
    @Nested
    @DisplayName("GET /forms/allEForms")
    class GetAllEForms {

        @Test
        @DisplayName("should return 200 with all eform names")
        void shouldReturn200WithAllEFormNames_whenEFormsExist() {
            EForm eform = new EForm();
            eform.setFormName("Intake Form");

            when(mockFormsManager.findByStatus(any(LoggedInInfo.class), eq(true), eq(EFormSortOrder.NAME)))
                .thenReturn(List.of(eform));

            Response response = request().path("/forms/allEForms").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no eforms exist")
        void shouldReturn200WithEmptyList_whenNoEFormsExist() {
            when(mockFormsManager.findByStatus(any(LoggedInInfo.class), eq(true), eq(EFormSortOrder.NAME)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/forms/allEForms").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    /** Tests for GET /forms/allEncounterForms endpoint. */
    @Nested
    @DisplayName("GET /forms/allEncounterForms")
    class GetAllEncounterForms {

        @Test
        @DisplayName("should return 200 with all encounter form names")
        void shouldReturn200WithAllEncounterForms_whenFormsExist() {
            EncounterForm form = new EncounterForm();
            form.setFormName("Physical Exam");
            form.setFormTable("formPhysicalExam");

            when(mockFormsManager.getAllEncounterForms()).thenReturn(List.of(form));

            Response response = request().path("/forms/allEncounterForms").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    /** Tests for GET /forms/groupNames endpoint. */
    @Nested
    @DisplayName("GET /forms/groupNames")
    class GetGroupNames {

        @Test
        @DisplayName("should return 200 with group names")
        void shouldReturn200WithGroupNames_whenGroupsExist() {
            when(mockFormsManager.getGroupNames()).thenReturn(List.of("Cardiology", "Neurology"));

            Response response = request().path("/forms/groupNames").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no groups exist")
        void shouldReturn200WithEmptyList_whenNoGroupsExist() {
            when(mockFormsManager.getGroupNames()).thenReturn(Collections.emptyList());

            Response response = request().path("/forms/groupNames").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
