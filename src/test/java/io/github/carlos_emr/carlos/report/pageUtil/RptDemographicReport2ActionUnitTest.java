/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
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
package io.github.carlos_emr.carlos.report.pageUtil;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.data.DemographicSets;
import io.github.carlos_emr.carlos.report.data.RptDemographicQuery2Builder;
import io.github.carlos_emr.carlos.report.data.RptDemographicQuery2Loader;
import io.github.carlos_emr.carlos.report.data.RptDemographicQuery2Saver;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pins #4225 at the actual bound-form/action boundary without constructing a writer on refusal. */
@DisplayName("Demographic report conditional mutation methods")
@Tag("unit")
@Tag("report")
@Tag("security")
class RptDemographicReport2ActionUnitTest extends CarlosUnitTestBase {
    private MockedStatic<ServletActionContext> servlet;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LoggedInInfo login;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(login, "_report", "r", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servlet.close();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "GET, Save Query", "HEAD, Save Query", "PUT, Save Query", "DELETE, Save Query",
            "PATCH, Save Query", "OPTIONS, Save Query",
            "GET, Run Query And Save to Patient Set", "HEAD, Run Query And Save to Patient Set",
            "PUT, Run Query And Save to Patient Set", "DELETE, Run Query And Save to Patient Set",
            "PATCH, Run Query And Save to Patient Set", "OPTIONS, Run Query And Save to Patient Set"
    })
    void shouldRefuseNonPostBeforeAnyWriterOrQueryConstruction(String method, String query) throws Exception {
        request.setMethod(method);
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery(query);
        action.getModel().setSelect(new String[]{"demographic_no"});
        try (var saver = mockConstruction(RptDemographicQuery2Saver.class);
             var builder = mockConstruction(RptDemographicQuery2Builder.class,
                     (mock, context) -> when(mock.buildQuery(any(), any())).thenReturn(new ArrayList<>()));
             var sets = mockConstruction(DemographicSets.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(saver.constructed()).isEmpty();
            assertThat(builder.constructed()).isEmpty();
            assertThat(sets.constructed()).isEmpty();
            assertThat(request.getAttribute("formBean")).isNull();
        }
    }

    @Test
    void shouldPreserveAuthorizedPostSaveAndOriginalForm() throws Exception {
        request.setMethod("POST");
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery("Save Query");
        action.getModel().setQueryName("FAKE owned O'Neil & favourite");
        try (var saver = mockConstruction(RptDemographicQuery2Saver.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(saver.constructed()).hasSize(1);
            verify(saver.constructed().getFirst()).saveQuery(action.getModel());
            assertThat(request.getAttribute("formBean")).isSameAs(action.getModel());
        }
    }

    @Test
    void shouldPreserveAuthorizedPostPatientSetWithExactQueryResults() throws Exception {
        request.setMethod("POST");
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery("Run Query And Save to Patient Set");
        action.getModel().setSelect(new String[]{"demographic_no"});
        action.getModel().setSetName("FAKE-owned-set");
        ArrayList<ArrayList<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of("41")));
        rows.add(new ArrayList<>(List.of("43")));
        try (var builder = mockConstruction(RptDemographicQuery2Builder.class,
                     (mock, context) -> when(mock.buildQuery(login, action.getModel())).thenReturn(rows));
             var sets = mockConstruction(DemographicSets.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(sets.constructed()).hasSize(1);
            verify(sets.constructed().getFirst()).addDemographicSet("FAKE-owned-set", List.of("41", "43"));
            assertThat(request.getAttribute("searchedArray")).isSameAs(rows);
        }
    }

    @Test
    void shouldPreserveBareGetFormDisplay() throws Exception {
        request.setMethod("GET");
        var action = new RptDemographicReport2Action();
        try (var saver = mockConstruction(RptDemographicQuery2Saver.class);
             var builder = mockConstruction(RptDemographicQuery2Builder.class);
             var sets = mockConstruction(DemographicSets.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("formBean")).isSameAs(action.getModel());
            assertThat(saver.constructed()).isEmpty();
            assertThat(builder.constructed()).isEmpty();
            assertThat(sets.constructed()).isEmpty();
        }
    }

    @Test
    void shouldPreserveReadOnlyGetQueryExecution() throws Exception {
        request.setMethod("GET");
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery("Run Query");
        ArrayList<ArrayList<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of("41")));
        try (var builder = mockConstruction(RptDemographicQuery2Builder.class,
                     (mock, context) -> when(mock.buildQuery(login, action.getModel())).thenReturn(rows));
             var saver = mockConstruction(RptDemographicQuery2Saver.class);
             var sets = mockConstruction(DemographicSets.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("searchedArray")).isSameAs(rows);
            assertThat(saver.constructed()).isEmpty();
            assertThat(sets.constructed()).isEmpty();
        }
    }

    @Test
    void shouldPreserveReadOnlyGetFavouriteLoading() throws Exception {
        request.setMethod("GET");
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery("Load Query");
        var loaded = new RptDemographicReport2Form();
        try (var loader = mockConstruction(RptDemographicQuery2Loader.class,
                     (mock, context) -> when(mock.queryLoader(action.getModel())).thenReturn(loaded));
             var saver = mockConstruction(RptDemographicQuery2Saver.class);
             var sets = mockConstruction(DemographicSets.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("formBean")).isSameAs(loaded);
            assertThat(saver.constructed()).isEmpty();
            assertThat(sets.constructed()).isEmpty();
        }
    }

    @Test
    void shouldPreserveReadOnlyGetStudyPreparation() throws Exception {
        request.setMethod("GET");
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery("Add to Study");
        action.getModel().setStudyId("FAKE-owned-study");
        ArrayList<ArrayList<String>> rows = new ArrayList<>();
        try (var builder = mockConstruction(RptDemographicQuery2Builder.class,
                     (mock, context) -> when(mock.buildQuery(login, action.getModel())).thenReturn(rows));
             var saver = mockConstruction(RptDemographicQuery2Saver.class);
             var sets = mockConstruction(DemographicSets.class)) {
            assertThat(action.execute()).isEqualTo("addToStudy");
            assertThat(request.getAttribute("searchedArray")).isSameAs(rows);
            assertThat(request.getAttribute("studyId")).isEqualTo("FAKE-owned-study");
            assertThat(saver.constructed()).isEmpty();
            assertThat(sets.constructed()).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"GET, Save Query", "HEAD, Save Query", "POST, Save Query",
            "GET, Run Query And Save to Patient Set", "HEAD, Run Query And Save to Patient Set",
            "POST, Run Query And Save to Patient Set"})
    void shouldPreserveReportAuthorizationBeforeEitherMutationGate(String method, String query) {
        request.setMethod(method);
        when(security.hasPrivilege(login, "_report", "r", null)).thenReturn(false);
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery(query);
        try (var saver = mockConstruction(RptDemographicQuery2Saver.class);
             var builder = mockConstruction(RptDemographicQuery2Builder.class);
             var sets = mockConstruction(DemographicSets.class)) {
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_report)");
            assertThat(saver.constructed()).isEmpty();
            assertThat(builder.constructed()).isEmpty();
            assertThat(sets.constructed()).isEmpty();
        }
    }
}
