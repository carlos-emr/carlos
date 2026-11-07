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
 * Modifications made by CARLOS Contributors in 2026: enforce CDS maintenance and disclosure compatibility.
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.DefaultRoleAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.utility.ProgramAccessCache;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.print.OscarChartPrinter;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint;
import io.github.carlos_emr.carlos.casemgmt.service.ClientImageManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.dao.CaisiAccessTypeDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.ResponseDefaultsFilter;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.util.LabelValueBean;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterConfig;

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
    private io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService projections;
    private ActionContext previousContext;
    private EctProgramManager defaultPrograms;

    @Test
    void shouldRefusePdfBeforeWritingBytes_whenStructuredChartCannotBeProjected() throws Exception {
        org.mockito.Mockito.doThrow(new io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException())
                .when(projections).requireWholeChartSupported(101);
        fullChart.print();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("cannot represent");
        assertThat(response.getContentType()).startsWith("text/plain");
    }

    @BeforeEach
    void prepareOwnChart() {
        projections = createAndRegisterMock(io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService.class);
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
        // Legacy positive fixtures genuinely have no configured programs unless a case says otherwise.
        defaultPrograms = mock(EctProgramManager.class);
        when(defaultPrograms.getProgramBeans("4249", null)).thenReturn(List.of());
        StaticWebApplicationContext web = new StaticWebApplicationContext();
        web.getBeanFactory().registerSingleton("ectProgramManager", defaultPrograms);
        request.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, web);
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
    void shouldRenderUnknownDateAndSourceAuthor_forAnAuthorizedLockedImportedNote() throws Exception {
        useCanonicalAliases();
        CaseManagementNote imported = note(501L, "101", "Protected locked body");
        imported.setLocked(true);
        imported.setObservation_date(null);
        imported.setProviderNo("");
        imported.setProvider(null);
        when(manager.getNote("501")).thenReturn(imported);

        entry.displayNotes();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Unknown", "UNKNOWN")
                .doesNotContain("Protected locked body", "null", "DELETED");
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

    static Stream<Arguments> invalidPrintContexts() {
        return Stream.of("selectedPdf", "fullPdf").flatMap(operation ->
                Stream.of("forged", "ambiguous", "foreign", "blank")
                        .map(context -> Arguments.of(operation, context)));
    }

    @ParameterizedTest
    @MethodSource("invalidPrintContexts")
    void shouldRefuseInvalidChartContext_beforeClinicalReadsHeadersOrPdfConstruction(
            String operation, String invalid) {
        invalidContext(invalid);
        try (var selected = mockConstruction(CaseManagementPrint.class);
             var complete = mockConstruction(OscarChartPrinter.class)) {
            assertThatThrownBy(() -> read(operation)).isInstanceOf(SecurityException.class);
            assertThat(selected.constructed()).isEmpty();
            assertThat(complete.constructed()).isEmpty();
        }
        verifyNoInteractions(manager, notes, demographics, allergies);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"forged", "ambiguous", "foreign", "blank"})
    void shouldRefuseInvalidContext_beforeStandaloneCppReadsOrRendering(String invalid) {
        invalidContext(invalid);
        OscarChartPrinter printer = mock(OscarChartPrinter.class);
        assertThatThrownBy(() -> fullChart.printCppItem(printer, "Medical History", "MedHistory", 101))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(manager, notes, demographics, allergies, printer);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
    }

    @Test
    void shouldReturnControlledForbiddenWithoutPdfHeaders_whenSelectedNotesAreChartHidden() throws Exception {
        String token = rememberOtherWindow("101", "7");
        request.setParameter(ChartProgramContext.PARAMETER, token);
        CaseManagementEntryFormBean draft = new CaseManagementEntryFormBean();
        CaseManagementNote unsaved = note(501L, "101", "Keep exact unsaved draft café 中文");
        draft.setCaseNote(unsaved);
        String formKey = ChartProgramContext.formKey(request, "101");
        request.getSession().setAttribute(formKey, draft);
        try (var renderer = mockConstruction(CaseManagementPrint.class, (mock, context) ->
                doThrow(new CaseManagementPrint.NoteAccessDeniedException()).when(mock).doPrint(
                        eq(login), eq(101), anyBoolean(), any(String[].class), anyBoolean(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), eq(request), any()))) {
            entry.print();
            assertThat(renderer.constructed()).hasSize(1);
        }
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getErrorMessage()).isEqualTo("Selected note access denied");
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(request.getSession().getAttribute(formKey)).isSameAs(draft);
        assertThat(draft.getCaseNote()).isSameAs(unsaved);
        assertThat(unsaved.getNote()).isEqualTo("Keep exact unsaved draft café 中文");
        verifyNoInteractions(manager, notes, demographics, allergies);
        logActionMock.verifyNoInteractions();
    }

    static Stream<Arguments> printErrorsWithCharsetWarnings() {
        return Stream.of(400, 403, 500).flatMap(status -> Stream.of(false, true)
                .map(warnings -> Arguments.of(status, warnings)));
    }

    @ParameterizedTest
    @MethodSource("printErrorsWithCharsetWarnings")
    void shouldPreserveResponseDefaults_whenPrintErrorsDiscardPdfOutput(int status, boolean warnings) throws Exception {
        ResponseDefaultsFilter filter = new ResponseDefaultsFilter();
        MockFilterConfig configuration = new MockFilterConfig();
        configuration.addInitParameter("warnCharsetCacheChange", Boolean.toString(warnings));
        filter.init(configuration);
        response.addCookie(new Cookie("FAKE-print-session", "retained"));
        response.setHeader("Content-Encoding", "gzip");
        CaseManagementEntryFormBean draft = new CaseManagementEntryFormBean();
        CaseManagementNote unsaved = note(501L, "101", "Keep exact draft\n  H&P café 中文 ");
        draft.setCaseNote(unsaved);
        String formKey = ChartProgramContext.formKey(request, "101");
        request.getSession().setAttribute(formKey, draft);
        if (status == 400) {
            request.setParameter("pType", "dates");
            request.setParameter("pStartDate", "not-a-date");
            request.setParameter("pEndDate", "not-a-date");
        }
        try (var renderer = mockConstruction(CaseManagementPrint.class, (mock, context) ->
                doAnswer(call -> {
                    if (status == 403) throw new CaseManagementPrint.NoteAccessDeniedException();
                    // A failed merge can leave uncommitted PDF bytes, which must be discarded.
                    call.<OutputStream>getArgument(13).write("%PDF-FAKE partial clinical bytes".getBytes(StandardCharsets.UTF_8));
                    throw new IOException("FAKE PDF rendering failure");
                }).when(mock).doPrint(eq(login), eq(101), anyBoolean(), any(String[].class),
                        anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                        any(), any(), eq(request), any()))) {
            filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
                entry.response = (HttpServletResponse) filteredResponse;
                try {
                    entry.print();
                } catch (Exception failure) {
                    throw new ServletException(failure);
                }
            });
            assertThat(renderer.constructed()).hasSize(status == 400 ? 0 : 1);
        }
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.isCommitted()).as("sendError prevents post-chain header repair").isTrue();
        assertThat(response.getHeader("X-Permitted-Cross-Domain-Policies")).isEqualTo("none");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeader("Permissions-Policy")).isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, no-cache, must-revalidate");
        assertThat(response.getCookie("FAKE-print-session")).isNotNull();
        assertThat(response.getCookie("FAKE-print-session").getValue()).isEqualTo("retained");
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getHeader("Content-Length")).isNull();
        assertThat(response.getHeader("Content-Encoding")).isNull();
        assertThat(request.getSession().getAttribute(formKey)).isSameAs(draft);
        assertThat(draft.getCaseNote()).isSameAs(unsaved);
        assertThat(unsaved.getNote()).isEqualTo("Keep exact draft\n  H&P café 中文 ");
        verifyNoInteractions(manager, notes, demographics, allergies);
    }

    static Stream<Arguments> readableFullChartContexts() {
        return Stream.of("signed", "legacy").flatMap(context ->
                Stream.of("same-role", "program-grant", "default-grant")
                        .map(grant -> Arguments.of(context, grant)));
    }

    @ParameterizedTest
    @MethodSource("readableFullChartContexts")
    void shouldFilterEveryFullChartCollectionWithOneCapturedProgram_whileKeepingAllowedSharedNotes(
            String context, String grant) throws Exception {
        String original = rememberOtherWindow("101", "7");
        if ("signed".equals(context)) {
            request.setParameter(ChartProgramContext.PARAMETER, original);
            rememberOtherWindow("101", "8");
        }
        String readableRole = "same-role".equals(grant) ? "7" : "8";
        CaseManagementNote visible = roleNote(601L, "Allowed other-program body", "8", readableRole);
        CaseManagementNote hidden = roleNote(602L, "Hidden originating-program body", "7", "9");
        CaseManagementNote visibleCpp = roleNote(603L, "Allowed shared CPP", "8", readableRole);
        CaseManagementNote hiddenCpp = roleNote(604L, "Hidden originating CPP", "7", "9");
        cpp(visibleCpp);
        cpp(hiddenCpp);
        List<CaseManagementNote> allNotes = List.of(visible, hidden, visibleCpp, hiddenCpp);
        List<CaseManagementNote> allCpp = List.of(visibleCpp, hiddenCpp);
        when(notes.getMostRecentNotes(101)).thenReturn(allNotes);
        when(notes.findNotesByDemographicAndIssueCode(eq(101), any(String[].class))).thenReturn(allCpp);
        ownDemographic();
        try (RealVisibility policy = new RealVisibility(grant);
             var contexts = mockStatic(ChartProgramContext.class, CALLS_REAL_METHODS);
             var renderer = mockConstruction(OscarChartPrinter.class, (mock, construction) -> {
                 // A second same-patient chart changes compatibility state after capture.
                 rememberOtherWindow("101", "8");
             })) {
            fullChart.print();
            OscarChartPrinter printer = renderer.constructed().getFirst();
            verify(printer).printNotes(List.of(visible), true);
            for (String header : List.of("Social History", "Medical History", "Ongoing Concerns", "Reminders",
                    "Family History", "Risk Factors", "Other Medications")) {
                verify(printer).printCPPItem(header, List.of(visibleCpp));
            }
            verify(manager, times(8)).filterNotes(eq(login), eq("4249"), anyCollection(), eq("7"));
            verify(manager, never()).filterNotes(any(), anyString(), anyCollection(), eq("8"));
            contexts.verify(() -> ChartProgramContext.resolve(request), times(1));
        }
        assertThat(allNotes).containsExactly(visible, hidden, visibleCpp, hiddenCpp);
        assertThat(allCpp).containsExactly(visibleCpp, hiddenCpp);
        assertThat(visible.getProgram_no()).isEqualTo("8");
        assertThat(hidden.getNote()).isEqualTo("Hidden originating-program body");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"full", "cpp"})
    void shouldApplyTheAuthenticatedDefaultBeforeRendering_whenFullChartContextIsAbsent(String mode) throws Exception {
        when(defaultPrograms.getProgramBeans("4249", null)).thenReturn(List.of(new LabelValueBean("FAKE restricted", "7")));
        when(defaultPrograms.getDefaultProgramId("4249")).thenReturn(7);
        request.setParameter("programId", "0");
        request.setParameter("case_program_id", "0");
        request.getSession().setAttribute("case_program_id", "0");
        rememberOtherWindow("202", "8");
        CaseManagementNote visible = roleNote(621L, "Visible default-program body", "8", "7");
        CaseManagementNote hidden = roleNote(622L, "Hidden default-program body", "7", "9");
        CaseManagementNote visibleCpp = roleNote(623L, "Visible default-program CPP", "8", "7");
        CaseManagementNote hiddenCpp = roleNote(624L, "Hidden default-program CPP", "7", "9");
        cpp(visibleCpp);
        cpp(hiddenCpp);
        when(notes.getMostRecentNotes(101)).thenReturn(List.of(visible, hidden, visibleCpp, hiddenCpp));
        when(notes.findNotesByDemographicAndIssueCode(eq(101), any(String[].class)))
                .thenReturn(List.of(visibleCpp, hiddenCpp));
        ownDemographic();
        try (RealVisibility policy = new RealVisibility("same-role")) {
            if ("full".equals(mode)) {
                try (var renderer = mockConstruction(OscarChartPrinter.class, (mock, construction) ->
                        rememberOtherWindow("101", "8"))) {
                    fullChart.print();
                    OscarChartPrinter printer = renderer.constructed().getFirst();
                    verify(printer).printNotes(List.of(visible), true);
                    verify(printer, times(7)).printCPPItem(anyString(), eq(List.of(visibleCpp)));
                    verify(manager, times(8)).filterNotes(eq(login), eq("4249"), anyCollection(), eq("7"));
                }
            } else {
                OscarChartPrinter printer = mock(OscarChartPrinter.class);
                fullChart.printCppItem(printer, "Medical History", "MedHistory", 101);
                verify(printer).printCPPItem("Medical History", List.of(visibleCpp));
                verify(manager).filterNotes(eq(login), eq("4249"), anyCollection(), eq("7"));
                assertThat(ChartProgramContext.resolve(request)).isNull();
            }
        }
        assertThat(ChartProgramContext.token(request)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "0", "signed-null", "signed-zero"})
    void shouldPreserveLegacyNoProgramCollections_evenIfAnotherWindowChangesFallbackDuringRendering(
            String program) throws Exception {
        if ("0".equals(program)) rememberOtherWindow("101", "0");
        else if (program.startsWith("signed-")) {
            request.setParameter(ChartProgramContext.PARAMETER,
                    rememberOtherWindow("101", "signed-zero".equals(program) ? "0" : null));
        }
        CaseManagementNote first = roleNote(611L, "Legacy first body", "7", "7");
        CaseManagementNote second = roleNote(612L, "Legacy other body", "8", "9");
        List<CaseManagementNote> all = List.of(first, second);
        when(notes.getMostRecentNotes(101)).thenReturn(all);
        when(notes.findNotesByDemographicAndIssueCode(eq(101), any(String[].class))).thenReturn(all);
        ownDemographic();
        try (var contexts = mockStatic(ChartProgramContext.class, CALLS_REAL_METHODS);
             var renderer = mockConstruction(OscarChartPrinter.class, (mock, construction) ->
                     rememberOtherWindow("101", "8"))) {
            fullChart.print();
            OscarChartPrinter printer = renderer.constructed().getFirst();
            verify(printer).printNotes(all, true);
            verify(printer, times(7)).printCPPItem(anyString(), eq(all));
            contexts.verify(() -> ChartProgramContext.resolve(request), times(1));
        }
        verifyNoInteractions(manager);
        assertThat(all).containsExactly(first, second);
    }

    private void invalidContext(String invalid) {
        String original = rememberOtherWindow("101", "7");
        switch (invalid) {
            case "forged" -> request.setParameter(ChartProgramContext.PARAMETER, "MTAxOjc." + "A".repeat(43));
            case "ambiguous" -> request.setParameter(ChartProgramContext.PARAMETER,
                    original, rememberOtherWindow("101", "8"));
            case "foreign" -> request.setParameter(ChartProgramContext.PARAMETER, rememberOtherWindow("202", "7"));
            case "blank" -> request.setParameter(ChartProgramContext.PARAMETER, "");
            default -> throw new IllegalArgumentException(invalid);
        }
    }

    private String rememberOtherWindow(String patient, String program) {
        MockHttpServletRequest window = new MockHttpServletRequest();
        window.setSession(request.getSession());
        window.setParameter("demographicNo", patient);
        ChartProgramContext.remember(window, program);
        return ChartProgramContext.token(window);
    }

    private void ownDemographic() {
        Demographic patient = new Demographic();
        patient.setDemographicNo(101);
        when(demographics.getClientByDemographicNo(101)).thenReturn(patient);
    }

    private static CaseManagementNote roleNote(long id, String text, String program, String role) {
        CaseManagementNote note = note(id, "101", text);
        note.setProgram_no(program);
        note.setReporter_caisi_role(role);
        note.setIssues(Set.of());
        return note;
    }

    private static void cpp(CaseManagementNote note) {
        Issue issue = new Issue();
        issue.setCode("MedHistory");
        CaseManagementIssue link = new CaseManagementIssue();
        link.setIssue(issue);
        note.setIssues(Set.of(link));
    }

    /** Uses the real role filter; only persisted memberships, grants and caches are test doubles. */
    private final class RealVisibility implements AutoCloseable {
        private final MockedStatic<RoleCache> roles;
        private final MockedStatic<ProgramAccessCache> access;
        private final MockedStatic<CarlosProperties> properties;

        RealVisibility(String grant) {
            createAndRegisterMock(SecroleDao.class);
            createAndRegisterMock(DefaultRoleAccessDAO.class);
            createAndRegisterMock(CaisiAccessTypeDao.class);
            createAndRegisterMock(ProgramAccessDAO.class);
            roles = mockStatic(RoleCache.class);
            access = mockStatic(ProgramAccessCache.class);
            properties = mockStatic(CarlosProperties.class);
            CarlosProperties settings = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(settings);
            when(settings.getBooleanProperty("FILTER_ON_FACILITY", "true")).thenReturn(false);
            CaseManagementManagerImpl actual = new CaseManagementManagerImpl();
            ProgramProviderDAO memberships = mock(ProgramProviderDAO.class);
            RoleProgramAccessDAO defaults = mock(RoleProgramAccessDAO.class);
            actual.setProgramProviderDao(memberships);
            actual.setRoleProgramAccessDAO(defaults);
            ProgramProvider membership = new ProgramProvider();
            membership.setRole(role(7L, "Doctor"));
            when(memberships.getProgramProviderByProviderProgramId("4249", 7L)).thenReturn(List.of(membership));
            roles.when(() -> RoleCache.getRole(7L)).thenReturn(membership.getRole());
            roles.when(() -> RoleCache.getRole(8L)).thenReturn(role(8L, "Nurse"));
            roles.when(() -> RoleCache.getRole(9L)).thenReturn(role(9L, "Restricted"));
            var permissions = new HashMap<String, ProgramAccess>();
            if ("program-grant".equals(grant)) {
                ProgramAccess permission = new ProgramAccess();
                permission.setAllRoles(true);
                permissions.put("read nurse notes", permission);
            }
            if ("default-grant".equals(grant)) when(defaults.hasAccess("read nurse notes", 7L)).thenReturn(true);
            access.when(() -> ProgramAccessCache.getAccessMap(7L)).thenReturn(permissions);
            when(manager.filterNotes(eq(login), eq("4249"), anyCollection(), anyString())).thenAnswer(call ->
                    actual.filterNotes(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        }

        @Override
        public void close() {
            properties.close();
            access.close();
            roles.close();
        }
    }

    private static Secrole role(long id, String name) {
        Secrole role = new Secrole();
        role.setId(id);
        role.setName(name);
        return role;
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
