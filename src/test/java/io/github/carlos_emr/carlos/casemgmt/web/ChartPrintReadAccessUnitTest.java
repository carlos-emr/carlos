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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.print.OscarChartPrinter;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint;
import io.github.carlos_emr.carlos.casemgmt.service.ClientImageManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.struts2.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

/**
 * Real action boundaries, with only persistence and PDF rendering mocked.
 * @since 2026-10-25
 */
@Tag("unit")
@Tag("security")
@Tag("casemgmt")
class ChartPrintReadAccessUnitTest extends CarlosUnitTestBase {
    private CaseManagementEntry2Action entry;
    private EChartPrint2Action fullChart;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo login;
    private SecurityInfoManager security;
    private CaseManagementManager manager;
    private CaseManagementNoteDAO notes;
    private DemographicDao demographics;
    private AllergyDao allergies;
    private ActionContext previousContext;

    @BeforeEach
    void prepareOwnChart() {
        notes = createAndRegisterMock(CaseManagementNoteDAO.class);
        createAndRegisterMock(CaseManagementIssueDAO.class);
        createAndRegisterMock(CaseManagementNoteExtDAO.class);
        createAndRegisterMock(IssueDAO.class);
        createAndRegisterMock(CasemgmtNoteLockDao.class);
        createAndRegisterMock(TicklerManager.class);
        createAndRegisterMock(ClientImageManager.class);
        createAndRegisterMock(ProviderManager.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        manager = createAndRegisterMock(CaseManagementManager.class);
        demographics = createAndRegisterMock(DemographicDao.class);
        allergies = createAndRegisterMock(AllergyDao.class);
        request = new MockHttpServletRequest("POST", "/CaseManagementEntry");
        response = new MockHttpServletResponse();
        previousContext = ActionContext.getContext();
        ActionContext.of(new HashMap<>()).withServletRequest(request).withServletResponse(response).bind();
        entry = new CaseManagementEntry2Action();
        fullChart = new EChartPrint2Action();
        entry.request = request;
        entry.response = response;
        fullChart.request = request;
        fullChart.response = response;
        request.setParameter("demographicNo", "101");
        request.setParameter("notes2print", "501");
        request.setParameter("printCPP", "false");
        request.setParameter("printRx", "false");
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("4249");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        for (String module : List.of("_demographic", "_eChart", "_casemgmt.notes")) {
            when(security.hasPrivilege(login, module, "r", "101")).thenReturn(true);
        }
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(true);
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(true);
        when(manager.getNote("501")).thenReturn(note(501L, "101", "Own <literal> & note"));
    }

    @AfterEach
    void restoreActionContext() {
        if (previousContext == null) ActionContext.clear();
        else previousContext.bind();
    }

    static Stream<Arguments> deniedReads() {
        return Stream.of("selectedPdf", "html", "fullPdf").flatMap(operation ->
                Stream.of("_demographic", "_eChart", "_casemgmt.notes", "record lock", "missing login")
                        .map(boundary -> Arguments.of(operation, boundary)));
    }

    @ParameterizedTest
    @MethodSource("deniedReads")
    void shouldRefuseBeforeClinicalReadsOrRendering_whenPatientAccessIsDenied(String operation, String boundary) {
        if ("record lock".equals(boundary)) {
            when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(false);
        } else if ("missing login".equals(boundary)) {
            LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), null);
        } else {
            when(security.hasPrivilege(login, boundary, "r", "101")).thenReturn(false);
        }
        try (var selected = mockConstruction(CaseManagementPrint.class);
             var complete = mockConstruction(OscarChartPrinter.class)) {
            assertThatThrownBy(() -> read(operation)).isInstanceOf(SecurityException.class);
            assertThat(selected.constructed()).isEmpty();
            assertThat(complete.constructed()).isEmpty();
        }
        verifyNoInteractions(manager, notes, demographics, allergies);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        logActionMock.verifyNoInteractions();
    }

    static Stream<Arguments> invalidPatients() {
        return Stream.of("selectedPdf", "html", "fullPdf").flatMap(operation ->
                Stream.of("missing", "malformed", "zero", "overflow", "conflicting")
                        .map(boundary -> Arguments.of(operation, boundary)));
    }

    @ParameterizedTest
    @MethodSource("invalidPatients")
    void shouldRejectBeforeAuthorizationOrReads_whenPatientIdentityIsInvalid(String operation, String boundary) throws Exception {
        switch (boundary) {
            case "missing" -> request.removeParameter("demographicNo");
            case "malformed" -> request.setParameter("demographicNo", "101bad");
            case "zero" -> request.setParameter("demographicNo", "0");
            case "overflow" -> request.setParameter("demographicNo", "2147483648");
            case "conflicting" -> request.setParameter("demographic_no", "202");
            default -> throw new IllegalArgumentException(boundary);
        }
        try (var selected = mockConstruction(CaseManagementPrint.class);
             var complete = mockConstruction(OscarChartPrinter.class)) {
            read(operation);
            assertThat(selected.constructed()).isEmpty();
            assertThat(complete.constructed()).isEmpty();
        }
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        verifyNoInteractions(security, manager, notes, demographics, allergies);
    }

    @Test
    void shouldKeepThePublicHtmlRendererGuarded_whenInvokedWithoutTheDispatchMethod() {
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(false);
        StringWriter output = new StringWriter();
        assertThatThrownBy(() -> entry.doDisplayNotes(request, new PrintWriter(output)))
                .isInstanceOf(SecurityException.class);
        assertThat(output.toString()).isEmpty();
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"print", "displayNotes"})
    void shouldKeepTheExistingEditorDraftIntact_whenDispatchRefusesAPrint(String operation) {
        request.setParameter("method", operation);
        when(security.hasPrivilege(login, "_demographic", "r", null)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(false);
        CaseManagementEntryFormBean form = new CaseManagementEntryFormBean();
        CaseManagementNote draft = note(501L, "101", "Retained editor draft\n  Exact spacing ");
        form.setCaseNote(draft);
        var issues = new ArrayList<CheckBoxBean>();
        form.setIssueCheckList(issues);
        String key = ChartProgramContext.formKey(request, "101");
        request.getSession().setAttribute(key, form);

        assertThatThrownBy(entry::execute).isInstanceOf(SecurityException.class);

        assertThat(request.getSession().getAttribute(key)).isSameAs(form);
        assertThat(form.getCaseNote()).isSameAs(draft);
        assertThat(draft.getNote()).isEqualTo("Retained editor draft\n  Exact spacing ");
        assertThat(form.getIssueCheckList()).isSameAs(issues);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(manager, notes, demographics, allergies);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "missing", "hidden", "outside domain"})
    void shouldRefuseTheWholeHtmlSelectionBeforeOutput_whenAnyNoteIsNotAuthorized(String boundary) throws Exception {
        request.setParameter("notes2print", "501,502");
        CaseManagementNote second = note(502L, "foreign".equals(boundary) ? "202" : "101", "Private second note");
        when(manager.getNote("502")).thenReturn("missing".equals(boundary) ? null : second);
        if ("hidden".equals(boundary)) {
            ChartProgramContext.remember(request, "7");
            when(manager.filterNotes(eq(login), eq("4249"), anyCollection(), eq("7"))).thenAnswer(call -> {
                var selection = call.<List<CaseManagementNote>>getArgument(2);
                return selection.getFirst().getId() == 501L ? selection : List.of();
            });
        }
        if ("outside domain".equals(boundary)) when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);
        entry.displayNotes();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getContentType()).isNull();
        assertThat(second.getNote()).isEqualTo("Private second note");
        verifyNoInteractions(notes, demographics, allergies);
    }

    @ParameterizedTest
    @ValueSource(strings = {"501,invalid", "-1", "0", "9223372036854775808", "501,"})
    void shouldRejectTheWholeHtmlSelectionBeforeNoteReads_whenAnyIdentifierIsInvalid(String selection) throws Exception {
        request.setParameter("notes2print", selection);
        entry.displayNotes();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEmpty();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRenderOnlyEscapedOwnText_whenCanonicalPatientAndNoteAccessAreGranted() throws Exception {
        useCanonicalAliases();
        when(manager.getNote("501")).thenReturn(note(501L, "101", "Own <literal> & note café 中文"));
        entry.displayNotes();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("text/html");
        assertThat(response.getContentAsString()).contains("Own &lt;literal&gt; &amp; note café 中文").doesNotContain("Own <literal>");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)).contains("café 中文");
        verify(manager).getNote("501");
        verifyNoMoreInteractions(notes, demographics, allergies);
    }

    @Test
    void shouldKeepTheOriginalProgramNoteReadable_whenAnotherProgramOfTheSamePatientOpensLater() throws Exception {
        ChartProgramContext.remember(request, "7");
        String originalContext = ChartProgramContext.token(request);
        request.setParameter(ChartProgramContext.PARAMETER, originalContext);
        MockHttpServletRequest otherChart = new MockHttpServletRequest();
        otherChart.setSession(request.getSession());
        otherChart.setParameter("demographicNo", "101");
        ChartProgramContext.remember(otherChart, "8");
        CaseManagementNote own = note(501L, "101", "Visible original-program note");
        own.setProgram_no("7");
        when(manager.getNote("501")).thenReturn(own);
        when(manager.filterNotes(login, "4249", List.of(own), "7")).thenReturn(List.of(own));

        entry.displayNotes();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Visible original-program note");
        verify(manager).filterNotes(login, "4249", List.of(own), "7");
        verify(manager, never()).filterNotes(any(), anyString(), anyCollection(), eq("8"));
    }

    @Test
    void shouldRefuseTheOriginalProgramHiddenNote_whenTheNewerProgramCanReadIt() throws Exception {
        ChartProgramContext.remember(request, "7");
        request.setParameter(ChartProgramContext.PARAMETER, ChartProgramContext.token(request));
        MockHttpServletRequest otherChart = new MockHttpServletRequest();
        otherChart.setSession(request.getSession());
        otherChart.setParameter("demographicNo", "101");
        ChartProgramContext.remember(otherChart, "8");
        CaseManagementNote hidden = note(501L, "101", "Hidden note");
        hidden.setProgram_no("8");
        when(manager.getNote("501")).thenReturn(hidden);
        when(manager.filterNotes(login, "4249", List.of(hidden), "7")).thenReturn(List.of());
        when(manager.filterNotes(login, "4249", List.of(hidden), "8")).thenReturn(List.of(hidden));

        entry.displayNotes();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
        verify(manager).filterNotes(login, "4249", List.of(hidden), "7");
        verify(manager, never()).filterNotes(any(), anyString(), anyCollection(), eq("8"));
    }

    @Test
    void shouldPassOnlyTheCanonicalAuthorizedPatientToPdfAssembly_whenSelectedChartReadIsGranted() throws Exception {
        useCanonicalAliases();
        try (var renderer = mockConstruction(CaseManagementPrint.class)) {
            entry.print();
            assertThat(renderer.constructed()).hasSize(1);
            verify(renderer.constructed().getFirst()).doPrint(eq(login), eq(101), eq(false), aryEq(new String[]{"501"}),
                    eq(false), eq(false), eq(false), eq(false), eq(false), eq(false), isNull(), isNull(), eq(request), any());
        }
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        verifyNoInteractions(manager, notes, demographics, allergies);
        verify(security).hasPrivilege(login, "_eChart", "r", "101");
        verify(security).hasPrivilege(login, "_casemgmt.notes", "r", "101");
    }

    @Test
    void shouldReadOnlyTheCanonicalPatientAndBuildTheCompletePdf_whenFullChartReadIsGranted() throws Exception {
        useCanonicalAliases();
        Demographic patient = new Demographic();
        patient.setDemographicNo(101);
        when(demographics.getClientByDemographicNo(101)).thenReturn(patient);
        try (var renderer = mockConstruction(OscarChartPrinter.class)) {
            fullChart.print();
            assertThat(renderer.constructed()).hasSize(1);
            verify(renderer.constructed().getFirst()).setDemographic(patient);
            verify(renderer.constructed().getFirst()).printMasterRecord();
            verify(renderer.constructed().getFirst()).close();
        }
        verify(demographics).getClientByDemographicNo(101);
        verify(demographics, never()).getClientByDemographicNo(202);
        verify(notes).getMostRecentNotes(101);
        assertThat(response.getHeader("Content-Disposition")).contains("101.pdf");
    }

    private void useCanonicalAliases() {
        request.setParameter("demographicNo", "00101");
        request.setParameter("demographic_no", "101");
    }

    private String read(String operation) throws Exception {
        return switch (operation) {
            case "selectedPdf" -> entry.print();
            case "html" -> entry.displayNotes();
            case "fullPdf" -> fullChart.print();
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private static CaseManagementNote note(long id, String patient, String text) {
        CaseManagementNote result = new CaseManagementNote();
        result.setId(id);
        result.setDemographic_no(patient);
        result.setNote(text);
        return result;
    }
}
