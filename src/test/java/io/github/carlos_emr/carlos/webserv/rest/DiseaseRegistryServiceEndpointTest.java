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
import java.util.Date;
import java.util.List;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.QuickListDao;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.QuickList;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DiagnosisTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DxQuickList;
import io.github.carlos_emr.carlos.webserv.rest.to.model.IssueTo1;

/**
 * HTTP-level endpoint tests for {@link DiseaseRegistryService} using CXF local transport.
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
@DisplayName("DiseaseRegistryService REST endpoint tests")
class DiseaseRegistryServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private QuickListDao mockQuickListDao;

    @Mock
    private DxresearchDAO mockDxresearchDao;

    @Mock
    private IssueDAO mockIssueDao;

    @Override
    protected Object getServiceBean() {
        DiseaseRegistryService service = new DiseaseRegistryService();
        injectDependency(service, "quickListDao", mockQuickListDao);
        injectDependency(service, "dxresearchDao", mockDxresearchDao);
        injectDependency(service, "issueDao", mockIssueDao);
        return service;
    }

    /** Tests for GET /dxRegisty/quickLists endpoint. */
    @Nested
    @DisplayName("GET /dxRegisty/quickLists")
    class GetQuickLists {

        @Test
        @DisplayName("should return 200 with quick lists as JSON")
        void shouldReturn200WithQuickLists_whenQuickListsExist() {
            QuickList ql = new QuickList();
            ql.setQuickListName("Common Diagnoses");
            ql.setCodingSystem("ICD9");
            ql.setDxResearchCode("250");

            when(mockQuickListDao.findAll()).thenReturn(List.of(ql));
            when(mockDxresearchDao.getDescription("ICD9", "250")).thenReturn("Diabetes mellitus");

            Response response = request().path("/dxRegisty/quickLists").get();

            assertThat(response.getStatus()).isEqualTo(200);
            List<DxQuickList> body = response.readEntity(new GenericType<List<DxQuickList>>() {});
            assertThat(body).hasSize(1);
            assertThat(body.get(0).getLabel()).isEqualTo("Common Diagnoses");
            assertThat(body.get(0).getDxList()).hasSize(1);
        }

        @Test
        @DisplayName("should return 200 with empty list when no quick lists exist")
        void shouldReturn200WithEmptyList_whenNoQuickListsExist() {
            when(mockQuickListDao.findAll()).thenReturn(Collections.emptyList());

            Response response = request().path("/dxRegisty/quickLists").get();

            assertThat(response.getStatus()).isEqualTo(200);
            List<DxQuickList> body = response.readEntity(new GenericType<List<DxQuickList>>() {});
            assertThat(body).isEmpty();
        }
    }

    /** Tests for POST /dxRegisty/findLikeIssue endpoint. */
    @Nested
    @DisplayName("POST /dxRegisty/findLikeIssue")
    class FindLikeIssue {

        @Test
        @DisplayName("should return 200 with matching issue")
        void shouldReturn200WithIssue_whenIssueFound() {
            Issue issue = new Issue();
            issue.setId(1L);
            issue.setCode("250");
            issue.setDescription("Diabetes mellitus");
            issue.setType("ICD9");
            issue.setPriority("high");
            issue.setRole("doctor");
            issue.setUpdate_date(new Date());
            issue.setSortOrderId(0);

            when(mockIssueDao.findIssueByTypeAndCode("ICD9", "250")).thenReturn(issue);

            DiagnosisTo1 dx = new DiagnosisTo1();
            dx.setCodingSystem("ICD9");
            dx.setCode("250");

            Response response = request().path("/dxRegisty/findLikeIssue")
                .post(Entity.json(dx));

            assertThat(response.getStatus()).isEqualTo(200);
            IssueTo1 body = response.readEntity(IssueTo1.class);
            assertThat(body.getCode()).isEqualTo("250");
            assertThat(body.getDescription()).isEqualTo("Diabetes mellitus");
        }
    }

    /** Tests for GET /dxRegisty/getDiseaseRegistry endpoint. */
    @Nested
    @DisplayName("GET /dxRegisty/getDiseaseRegistry")
    class GetDiseaseRegistry {

        @Test
        @DisplayName("should return 200 with disease registry entries")
        void shouldReturn200WithEntries_whenEntriesExist() {
            Dxresearch dx = new Dxresearch();
            dx.setDemographicNo(123);
            dx.setCodingSystem("ICD9");
            dx.setDxresearchCode("250");
            dx.setStatus('A');

            when(mockDxresearchDao.getByDemographicNo(123)).thenReturn(List.of(dx));

            Response response = request().path("/dxRegisty/getDiseaseRegistry")
                .query("demographicNo", 123)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no entries exist")
        void shouldReturn200WithEmptyList_whenNoEntriesExist() {
            when(mockDxresearchDao.getByDemographicNo(456)).thenReturn(Collections.emptyList());

            Response response = request().path("/dxRegisty/getDiseaseRegistry")
                .query("demographicNo", 456)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
