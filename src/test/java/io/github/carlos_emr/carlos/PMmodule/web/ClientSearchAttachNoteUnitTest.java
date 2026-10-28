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
package io.github.carlos_emr.carlos.PMmodule.web;

import java.util.List;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.PMmodule.service.ClientManager;
import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Each "attach note to client" popup carries its own note through its search, so a second chart's
 * popup cannot make the first one attach its note (issue #4673).
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("pmmodule")
@DisplayName("PMM attach-note popup keeps its own note")
class ClientSearchAttachNoteUnitTest extends CarlosUnitTestBase {

    private final MockHttpSession session = new MockHttpSession();
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        springUtilsMock.when(() -> SpringUtils.getBean(any(Class.class)))
                .thenAnswer(invocation -> mockedBeans.computeIfAbsent(invocation.getArgument(0),
                        type -> mock((Class<?>) type)));
        CaseManagementNoteDAO notes = mock(CaseManagementNoteDAO.class);
        when(notes.getNote(11L)).thenReturn(new CaseManagementNote());
        when(notes.getNote(22L)).thenReturn(new CaseManagementNote());
        registerMock(CaseManagementNoteDAO.class, notes);
        ClientManager clients = mock(ClientManager.class);
        when(clients.search(any())).thenReturn(List.of());
        registerMock(ClientManager.class, clients);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenAnswer(invocation -> request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
    }

    @Test
    void shouldSearchForThePopupsOwnNote_whenAnotherChartsPopupOpenedLater() {
        newRequest().setParameter("noteId", "11");
        new ClientSearchAction22Action().attachForm();
        assertThat(request.getAttribute("noteId")).isEqualTo("11");
        // Another chart's attach popup.
        newRequest().setParameter("noteId", "22");
        new ClientSearchAction22Action().attachForm();

        // The first popup searches, posting its own note back.
        newRequest().setParameter("noteId", "11");
        ClientSearchAction22Action search = new ClientSearchAction22Action();
        search.setCriteria(new ClientSearchFormBean());
        search.attachSearch();

        assertThat(request.getAttribute("noteId")).isEqualTo("11");
        assertThat(session.getAttribute("noteId")).isNull();
    }

    @Test
    void shouldOfferNoNote_whenTheSearchPostsAnUnknownNote() {
        newRequest().setParameter("noteId", "99");
        ClientSearchAction22Action search = new ClientSearchAction22Action();
        search.setCriteria(new ClientSearchFormBean());

        search.attachSearch();

        assertThat(request.getAttribute("noteId")).isNull();
    }

    @Test
    void shouldKeepThePopupsNondefaultSourceContext_whenAnotherProgramPopupSearchesLater() {
        useSourcePatient();
        String first = sourceContext("202", "8");
        String second = sourceContext("202", "7");
        newRequest().setParameter("noteId", "11");
        request.setParameter("sourceChartContext", first);
        new ClientSearchAction22Action().attachForm();
        assertThat(request.getAttribute("sourceChartContext")).isEqualTo(first);

        newRequest().setParameter("noteId", "11");
        request.setParameter("sourceChartContext", second);
        new ClientSearchAction22Action().attachForm();

        newRequest().setParameter("noteId", "11");
        request.setParameter("sourceChartContext", first);
        ClientSearchAction22Action search = new ClientSearchAction22Action();
        search.setCriteria(new ClientSearchFormBean());
        search.attachSearch();

        assertThat(request.getAttribute("sourceChartContext")).isEqualTo(first);
        assertThat(request.getAttribute("noteId")).isEqualTo("11");
        assertThat(session.getAttribute("sourceChartContext")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0"})
    void shouldPreserveAnIssuedLegacySourceToken_insteadOfDroppingItsNonblankContext(String program) {
        useSourcePatient();
        String token = sourceContext("202", "null".equals(program) ? null : program);
        newRequest().setParameter("noteId", "11");
        request.setParameter("sourceChartContext", token);

        new ClientSearchAction22Action().attachForm();

        assertThat(request.getAttribute("sourceChartContext")).isEqualTo(token);
        assertThat(request.getAttribute("noteId")).isEqualTo("11");
    }

    @ParameterizedTest
    @ValueSource(strings = {"forged", "wrong-patient", "conflicting", "blank"})
    void shouldRefuseUntrustedSourceContext_beforePublishingAnAttachableNote(String invalid) {
        useSourcePatient();
        String valid = sourceContext("202", "7");
        newRequest().setParameter("noteId", "11");
        switch (invalid) {
            case "forged" -> request.setParameter("sourceChartContext", "forged");
            case "wrong-patient" -> request.setParameter("sourceChartContext", sourceContext("303", "7"));
            case "conflicting" -> request.setParameter("sourceChartContext", valid, sourceContext("202", "8"));
            default -> request.setParameter("sourceChartContext", "");
        }

        assertThatThrownBy(() -> new ClientSearchAction22Action().attachForm())
                .isInstanceOf(SecurityException.class);

        assertThat(request.getAttribute("noteId")).isNull();
        assertThat(request.getAttribute("sourceChartContext")).isNull();
    }

    private void useSourcePatient() {
        CaseManagementNote source = new CaseManagementNote();
        source.setDemographic_no("202");
        CaseManagementNoteDAO notes = mock(CaseManagementNoteDAO.class);
        when(notes.getNote(11L)).thenReturn(source);
        registerMock(CaseManagementNoteDAO.class, notes);
    }

    private String sourceContext(String patient, String program) {
        MockHttpServletRequest source = new MockHttpServletRequest();
        source.setSession(session);
        source.setParameter("demographicNo", patient);
        ChartProgramContext.remember(source, program);
        return ChartProgramContext.token(source);
    }

    private MockHttpServletRequest newRequest() {
        request = new MockHttpServletRequest("POST", "/carlos/PMmodule/ClientSearch2");
        request.setSession(session);
        return request;
    }
}
