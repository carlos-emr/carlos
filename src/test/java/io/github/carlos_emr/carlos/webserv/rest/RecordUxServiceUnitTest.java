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
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.conversion.summary.Summary;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryTo1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecordUxService} clinical-summary and print-stream authorization.
 *
 * <p>Verifies that {@code getFullSummmary()} — the shared entry point for the
 * eight clinical-summary REST shortcuts (family history, medical history,
 * ongoing concerns, other meds, reminders, risk factors, allergies,
 * preventions) — enforces a patient-specific {@code _eChart} read privilege
 * before returning any PHI. Uses a testable subclass overriding
 * {@code getLoggedInInfo()} to avoid requiring the CXF HTTP request context.</p>
 *
 * @since 2026-06-24
 * @see RecordUxService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RecordUxService clinical-summary and print authorization")
@Tag("read")
class RecordUxServiceUnitTest extends CarlosUnitTestBase {

    private static final Integer DEMOGRAPHIC_NO = 12345;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private LoggedInInfo loggedInInfo;
    private RecordUxService service;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        loggedInInfo.setIp("127.0.0.1");

        LoggedInInfo capturedInfo = loggedInInfo;
        service = new RecordUxService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should throw SecurityException without fetching summary when user lacks _eChart read privilege")
    void shouldThrowSecurityException_whenUserLacksEChartReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.getFullSummmary(DEMOGRAPHIC_NO, SummaryTo1.ALLERGIES))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart)");

        // Bean lookup for the summary must never happen once access is denied.
        springUtilsMock.verify(() -> SpringUtils.getBean(anyString()), never());
    }

    @Test
    @DisplayName("should perform a patient-specific privilege check using the demographicNo")
    void shouldPerformPatientSpecificCheck_withDemographicNo() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.getFullSummmary(DEMOGRAPHIC_NO, SummaryTo1.MEDICALHISTORY_CODE))
                .isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(loggedInInfo, "_eChart", "r", DEMOGRAPHIC_NO);
    }

    @Test
    @DisplayName("should return the summary when user has _eChart read privilege")
    void shouldReturnSummary_whenUserHasEChartReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), anyInt())).thenReturn(true);

        SummaryTo1 expected = new SummaryTo1();
        Summary summaryBean = mock(Summary.class);
        when(summaryBean.getSummary(eq(loggedInInfo), eq(DEMOGRAPHIC_NO), eq(SummaryTo1.FAMILYHISTORY_CODE)))
                .thenReturn(expected);
        springUtilsMock.when(() -> SpringUtils.getBean(anyString())).thenReturn(summaryBean);

        SummaryTo1 result = service.getFullSummmary(DEMOGRAPHIC_NO, SummaryTo1.FAMILYHISTORY_CODE);

        assertThat(result).isSameAs(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_eChart", "_casemgmt.notes", "record"})
    void shouldRefuseBeforeReturningAStream_whenThePrintPatientReadBoundaryIsDenied(String boundary) {
        grantPrintRead();
        if ("record".equals(boundary)) {
            when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(false);
        } else {
            when(mockSecurityInfoManager.hasPrivilege(loggedInInfo, boundary, "r", DEMOGRAPHIC_NO)).thenReturn(false);
        }
        RetainedDraft retained = retainedDraft();
        try (var printers = mockConstruction(CaseManagementPrint.class)) {
            assertThatThrownBy(() -> service.print(DEMOGRAPHIC_NO, "FAKE invalid options", retained.request()))
                    .isInstanceOf(ForbiddenException.class)
                    .satisfies(thrown -> assertThat(((ForbiddenException) thrown).getResponse().getStatus()).isEqualTo(403));
            assertThat(printers.constructed()).isEmpty();
        }
        assertRetainedDraft(retained);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void shouldRejectBeforeAuthorizationOrPrinting_whenTheRestPrintPatientIsNotPositive(Integer patient) {
        try (var printers = mockConstruction(CaseManagementPrint.class)) {
            assertThatThrownBy(() -> service.print(patient, "FAKE invalid options", new MockHttpServletRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .satisfies(thrown -> assertThat(((BadRequestException) thrown).getResponse().getStatus()).isEqualTo(400));
            assertThat(printers.constructed()).isEmpty();
        }
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    void shouldReturnALazyStream_whenAllPrintModuleAndRecordReadsAreGranted() {
        grantPrintRead();
        try (var printers = mockConstruction(CaseManagementPrint.class)) {
            assertThat(service.print(DEMOGRAPHIC_NO,
                    "{\"printType\":\"selected\",\"selectedList\":[\"501\"]}", new MockHttpServletRequest())).isNotNull();
            assertThat(printers.constructed()).isEmpty();
        }
        for (String module : new String[]{"_demographic", "_eChart", "_casemgmt.notes"}) {
            verify(mockSecurityInfoManager).hasPrivilege(loggedInInfo, module, "r", DEMOGRAPHIC_NO);
        }
        verify(mockSecurityInfoManager).isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO);
    }

    @Test
    void shouldRefuseWithoutOutputOrDraftChanges_whenThePdfStreamDeniesASelectedNote() throws Exception {
        assertPrintStreamFailure(new CaseManagementPrint.NoteAccessDeniedException(), 403);
    }

    @Test
    void shouldKeepRenderingFailuresAsServerErrors_whenThePdfStreamCannotBeGenerated() throws Exception {
        assertPrintStreamFailure(new IOException("FAKE PDF renderer failure"), 500);
    }

    @Test
    void shouldKeepTemporaryPathFailuresAsServerErrors_whenPdfFileValidationFails() throws Exception {
        assertPrintStreamFailure(new FileValidationException("FAKE PDF temporary path configuration"), 500);
    }

    @ParameterizedTest
    @ValueSource(strings = {"blank", "forged", "ambiguous", "foreign"})
    void shouldRefuseBeforeOutputOrClinicalReads_whenThePdfStreamContextIsInvalid(String invalid) throws Exception {
        grantPrintRead();
        CaseManagementManager manager = createAndRegisterMock(CaseManagementManager.class);
        PreventionManager preventions = createAndRegisterMock(PreventionManager.class);
        AllergyDao allergies = createAndRegisterMock(AllergyDao.class);
        RetainedDraft retained = retainedDraft();
        MockHttpServletRequest request = retained.request();
        String own = programToken(request, DEMOGRAPHIC_NO.toString(), "7");
        String other = programToken(request, DEMOGRAPHIC_NO.toString(), "8");
        switch (invalid) {
            case "blank" -> request.setParameter("chartContext", "");
            case "forged" -> request.setParameter("chartContext", "forged.context");
            case "ambiguous" -> request.setParameter("chartContext", own, other);
            case "foreign" -> {
                // The REST path is authoritative even if both query aliases match another token.
                request.setParameter("demographicNo", "67890");
                request.setParameter("demographic_no", "67890");
                request.setParameter("chartContext", programToken(request, "67890", "7"));
            }
            default -> throw new IllegalArgumentException(invalid);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var stream = service.print(DEMOGRAPHIC_NO,
                "{\"printType\":\"selected\",\"selectedList\":[\"501\"]}", request);

        // No CaseManagementPrint construction mock: the actual shared context boundary executes.
        assertThatThrownBy(() -> stream.write(output)).isInstanceOf(ForbiddenException.class)
                .satisfies(thrown -> {
                    assertThat(((ForbiddenException) thrown).getResponse().getStatus()).isEqualTo(403);
                    assertThat(thrown.getCause()).isInstanceOf(CaseManagementPrint.NoteAccessDeniedException.class);
                    assertThat(thrown.getCause().getCause()).isInstanceOf(SecurityException.class);
                });

        assertThat(output.toByteArray()).isEmpty();
        verifyNoInteractions(manager, preventions, allergies);
        assertRetainedDraft(retained);
    }

    /** Exercises the returned lazy stream itself, including the real REST exception mapping. */
    private void assertPrintStreamFailure(Exception failure, int expectedStatus) throws Exception {
        grantPrintRead();
        RetainedDraft retained = retainedDraft();
        MockHttpServletRequest request = retained.request();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (var printers = mockConstruction(CaseManagementPrint.class, (printer, context) ->
                doThrow(failure).when(printer).doPrint(any(), any(), anyBoolean(), any(),
                        anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                        any(), any(), any(), any()))) {
            var stream = service.print(DEMOGRAPHIC_NO,
                    "{\"printType\":\"selected\",\"selectedList\":[\"501\"]}", request);
            assertThat(printers.constructed()).as("PDF generation remains lazy until write").isEmpty();

            assertThatThrownBy(() -> stream.write(output)).isInstanceOf(WebApplicationException.class)
                    .satisfies(thrown -> {
                        WebApplicationException response = (WebApplicationException) thrown;
                        assertThat(response.getResponse().getStatus()).isEqualTo(expectedStatus);
                        assertThat(response.getCause()).isSameAs(failure);
                        if (expectedStatus == 403) assertThat(response).isInstanceOf(ForbiddenException.class);
                        else assertThat(response).isNotInstanceOf(ForbiddenException.class);
                    });

            assertThat(printers.constructed()).hasSize(1);
            verify(printers.constructed().getFirst()).doPrint(eq(loggedInInfo), eq(DEMOGRAPHIC_NO), eq(false),
                    eq(new String[]{"501"}), eq(false), eq(false), eq(false), eq(false), eq(false), eq(false),
                    eq(null), eq(null), eq(request), eq(output));
        }
        assertThat(output.toByteArray()).isEmpty();
        assertRetainedDraft(retained);
    }

    private RetainedDraft retainedDraft() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/rs/recordUX/12345/print");
        request.setParameter("demographicNo", DEMOGRAPHIC_NO.toString());
        CaseManagementEntryFormBean form = new CaseManagementEntryFormBean();
        CaseManagementNote draft = new CaseManagementNote();
        draft.setDemographic_no(DEMOGRAPHIC_NO.toString());
        draft.setNote("FAKE retained draft\n  H&P + café 中文 <literal> ");
        form.setCaseNote(draft);
        form.setIssueCheckList(new ArrayList<>());
        request.setParameter("chartContext", programToken(request, DEMOGRAPHIC_NO.toString(), "7"));
        String key = ChartProgramContext.formKey(request, DEMOGRAPHIC_NO.toString());
        request.getSession().setAttribute(key, form);
        Object bystander = new Object();
        request.getSession().setAttribute("FAKE-other-chart-draft", bystander);
        return new RetainedDraft(request, key, form, draft, form.getIssueCheckList(), bystander);
    }

    private void assertRetainedDraft(RetainedDraft before) {
        assertThat(before.request().getSession().getAttribute(before.key())).isSameAs(before.form());
        assertThat(before.form().getCaseNote()).isSameAs(before.note());
        assertThat(before.note().getNote()).isEqualTo("FAKE retained draft\n  H&P + café 中文 <literal> ");
        assertThat(before.form().getIssueCheckList()).isSameAs(before.issues());
        assertThat(before.request().getSession().getAttribute("FAKE-other-chart-draft")).isSameAs(before.bystander());
    }

    private String programToken(MockHttpServletRequest request, String patient, String program) {
        MockHttpServletRequest chart = new MockHttpServletRequest();
        chart.setSession(request.getSession());
        chart.setParameter("demographicNo", patient);
        ChartProgramContext.remember(chart, program);
        return ChartProgramContext.token(chart);
    }

    private void grantPrintRead() {
        for (String module : new String[]{"_demographic", "_eChart", "_casemgmt.notes"}) {
            when(mockSecurityInfoManager.hasPrivilege(loggedInInfo, module, "r", DEMOGRAPHIC_NO)).thenReturn(true);
        }
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(true);
    }

    private record RetainedDraft(MockHttpServletRequest request, String key, CaseManagementEntryFormBean form,
                                 CaseManagementNote note, Object issues, Object bystander) {
    }
}
