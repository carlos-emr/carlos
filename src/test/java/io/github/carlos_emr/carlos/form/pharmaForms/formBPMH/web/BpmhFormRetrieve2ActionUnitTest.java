/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.commn.model.FormBPMH;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhForm2Bean;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.business.BpmhForm2Handler;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Opening and saving the BPMH form (#4731): {@code fetch()} used a handler field that only
 * {@code save()} and {@code print()} set, so every open threw {@code NullPointerException}; the
 * page read a bean nothing exposed; and the save redirect left the application's context path.
 */
@DisplayName("BpmhFormRetrieve2Action Tests")
@Tag("unit")
@Tag("form")
class BpmhFormRetrieve2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private LoggedInInfo loggedInInfo;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private BpmhForm2Handler handler;

    private final BpmhForm2Bean filled = new BpmhForm2Bean();
    private BpmhForm2Bean handedToHandler;
    private BpmhFormRetrieve2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_demographic"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_demographic"), eq("r"), anyInt())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_form"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_form"), eq("w"), isNull())).thenReturn(true);
        when(handler.getBpmhFormBean()).thenReturn(filled);

        action = new BpmhFormRetrieve2Action() {
            @Override
            <T> T inReviewTransaction(java.util.function.Supplier<T> work) {
                return work.get();
            }

            @Override
            BpmhForm2Handler newHandler(BpmhForm2Bean bean) {
                handedToHandler = bean;
                return handler;
            }
        };
    }

    @AfterEach
    void tearDown() throws Exception {
        loggedInInfoMock.close();
        servletActionContextMock.close();
        mocks.close();
        ActionContext.clear();
    }

    @Test
    void shouldOpenNewForm_forPatientWithoutSavedForm() {
        when(request.getParameter("demographic_no")).thenReturn("1001");
        when(request.getParameter("formId")).thenReturn("0");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(handedToHandler).isNotNull();
        verify(handler).setDemographicNo(1001);
        verify(handler, never()).setFormHistory(any());
        verify(handler).populateFormBean();
        verify(request).setAttribute("bpmh", filled);
        assertThat(filled.getFormId()).as("a new form is id 0, which the page offers Save for").isEqualTo("0");
    }

    @Test
    void shouldOpenSavedForm_whenFormIdGiven() {
        when(request.getParameter("demographic_no")).thenReturn("1001");
        when(request.getParameter("formId")).thenReturn("7");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(handler).setFormHistory(7);
        verify(handler).populateFormBean();
        verify(request).setAttribute("bpmh", filled);
    }

    @Test
    void shouldRedirectWithinContextPath_afterSave() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn("save");
        when(request.getContextPath()).thenReturn("/carlos");
        when(handler.saveFormHistory()).thenReturn(42);
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setReviewToken("reviewed");
        filled.setReviewToken("reviewed");
        action.setForm(posted);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(handedToHandler).isNotSameAs(posted);
        assertThat(handedToHandler.getDemographicNo()).isEqualTo("1001");
        verify(handler).setDemographicNo(1001);
        verify(handler).saveFormHistory();
        assertThat(filled.getFormDate()).as("the saved copy carries the date it was prepared").isNotNull();
        assertThat(filled.getEditDate()).isNotNull();
        verify(response).sendRedirect("/carlos/formBPMH?method=fetch&demographic_no=1001&formId=42&provNo=");
    }

    @Test
    void shouldKeepOnlyEditableFields_fromPostedForm() {
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setFormId("0");
        posted.setNote("FAKE note");
        posted.setConfirm(true);
        posted.setAllergiesString("FAKE posted allergy");
        posted.setFamilyDrName("FAKE posted doctor");
        BpmhDrug row = new BpmhDrug();
        row.setId("55");
        row.setInstruction("continue");
        row.setWhat("posted what");
        BpmhDrug noId = new BpmhDrug();
        noId.setWhat("row without an id");
        posted.setDrugs(new java.util.ArrayList<>(java.util.List.of(row, noId)));

        BpmhForm2Bean editable = BpmhFormRetrieve2Action.editableFields(posted);

        assertThat(editable.getDemographicNo()).isEqualTo("1001");
        assertThat(editable.getFormId()).isEqualTo("0");
        assertThat(editable.getNote()).isEqualTo("FAKE note");
        assertThat(editable.isConfirm()).isTrue();
        assertThat(editable.getAllergiesString()).isNull();
        assertThat(editable.getFamilyDrName()).isNull();
        assertThat(editable.getDrugs()).hasSize(1);
        assertThat(editable.getDrugs().get(0).getId()).isEqualTo("55");
        assertThat(editable.getDrugs().get(0).getInstruction()).isEqualTo("continue");
        assertThat(editable.getDrugs().get(0).getWhat()).isNotEqualTo("posted what");
    }

    @Test
    void shouldRefuseOpeningNewForm_whenPatientIsRestrictedForUser() {
        when(request.getParameter("demographic_no")).thenReturn("1001");
        when(request.getParameter("formId")).thenReturn("0");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 1001)).thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");

        verify(handler, never()).populateFormBean();
        verify(request, never()).setAttribute(eq("bpmh"), any());
    }

    @Test
    void shouldCheckSavedFormsOwnPatient_notTheRequestedOne() {
        // The handler adopts the saved form's patient, so a request naming a patient the user may read
        // cannot open another patient's saved form.
        FormBPMH saved = new FormBPMH();
        saved.setDemographicNo(2002);
        when(handler.getFormHistory()).thenReturn(saved);
        when(request.getParameter("demographic_no")).thenReturn("1001");
        when(request.getParameter("formId")).thenReturn("7");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 2002)).thenReturn(false);

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);

        verify(handler, never()).populateFormBean();
    }

    @Test
    void shouldRefuseSaving_whenPatientIsRestrictedForUser() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn("save");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 1001)).thenReturn(false);
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        action.setForm(posted);

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);

        verify(handler, never()).saveFormHistory();
    }

    @ParameterizedTest
    @CsvSource({"GET, save", "HEAD, save", "GET, print", "HEAD, print"})
    @DisplayName("should answer 405 and store nothing when save or print is not posted")
    void shouldStoreNothing_whenSaveOrPrintIsNotPosted(String httpMethod, String method) throws Exception {
        when(request.getMethod()).thenReturn(httpMethod);
        when(request.getParameter("method")).thenReturn(method);
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setFormId("0");
        action.setForm(posted);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        verify(response).setHeader("Allow", "POST");
        verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(handedToHandler).as("no handler is built, so nothing is loaded or stored").isNull();
        verify(handler, never()).saveFormHistory();
    }

    @Test
    @DisplayName("should still open a form by GET")
    void shouldOpenForm_whenRequestedByGet() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getParameter("demographic_no")).thenReturn("1001");
        when(request.getParameter("formId")).thenReturn("0");

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        verify(handler).populateFormBean();
        verify(response, never()).setHeader("Allow", "POST");
    }

    @Test
    @DisplayName("should let a posted print through to the print path")
    void shouldReachPrint_whenPrintIsPosted() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn("print");
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setFormId("0");
        posted.setReviewToken("reviewed");
        filled.setReviewToken("reviewed");
        action.setForm(posted);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        // The print path built its handler and loaded the form; it then stops at the PDF template, which
        // this unit test has no servlet context for.
        assertThat(handedToHandler).isNotNull();
        verify(handler).populateFormBean();
        verify(response, never()).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "print"})
    void shouldShowChartConflictBeforeSavingOrPrinting(String method) throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn(method);
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setFormId("0");
        posted.setReviewToken("earlier-chart");
        filled.setReviewToken("current-chart");
        action.setForm(posted);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(response).setStatus(409);
        verify(request).setAttribute("bpmhReviewChanged", true);
        verify(handler, never()).saveFormHistory();
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "print"})
    void shouldRequireFormWritePrivilege_whenCreatingNewSnapshot(String method) {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn(method);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", "w", null)).thenReturn(false);
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setFormId("0");
        posted.setReviewToken("reviewed");
        filled.setReviewToken("reviewed");
        action.setForm(posted);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_form)");

        verify(handler, never()).populateFormBean();
        verify(handler, never()).saveFormHistory();
    }

    @Test
    void shouldAllowSavedHistoryPrint_withoutFormWritePrivilege() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn("print");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", "w", null)).thenReturn(false);
        FormBPMH saved = new FormBPMH();
        saved.setDemographicNo(1001);
        when(handler.getFormHistory()).thenReturn(saved);
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setFormId("7");
        action.setForm(posted);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(handler).setFormHistory(7);
        verify(handler).populateFormBean();
        verify(handler, never()).saveFormHistory();
    }

    @Test
    void shouldRejectNegativeFormId_beforePreparingPrint() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn("print");
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setFormId("-5");
        action.setForm(posted);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(handedToHandler).isNull();
        verify(handler, never()).populateFormBean();
    }

    @Test
    void shouldRejectWhitespaceReportedMedication_andPreserveRowText() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn("save");
        BpmhDrug reported = new BpmhDrug();
        reported.setReportedMedication("   ");
        reported.setPatientReportedUse("Takes at bedtime");
        reported.setInstruction("Clarify product name");
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setFormId("0");
        posted.setReviewToken("reviewed");
        posted.setDrugs(new java.util.ArrayList<>(java.util.List.of(reported)));
        filled.setReviewToken("reviewed");
        filled.setDrugs(new java.util.ArrayList<>(java.util.List.of(reported)));
        action.setForm(posted);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(request).setAttribute("bpmhInvalidReportedMedication", true);
        assertThat(handedToHandler.getDrugs()).hasSize(1);
        assertThat(handedToHandler.getDrugs().get(0).getPatientReportedUse()).isEqualTo("Takes at bedtime");
        assertThat(handedToHandler.getDrugs().get(0).getInstruction()).isEqualTo("Clarify product name");
        verify(handler, never()).saveFormHistory();
    }

    @Test
    void shouldSetNoStoreCacheHeadersAndDeleteTempPdf_whenPrintSucceeds(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.nio.file.Path generatedPdf = tempDir.resolve("bpmh-test.pdf");
        byte[] pdfBytes = "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        java.nio.file.Files.write(generatedPdf, pdfBytes);

        var servletContext = org.mockito.Mockito.mock(jakarta.servlet.ServletContext.class);
        when(servletContext.getRealPath(any())).thenReturn(tempDir.resolve("template.pdf").toString());
        servletActionContextMock.when(ServletActionContext::getServletContext).thenReturn(servletContext);

        var written = new java.io.ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
            @Override public void write(int b) { written.write(b); }
        });

        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn("print");
        when(handler.saveFormHistory()).thenReturn(42);
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDemographicNo("1001");
        posted.setFormId("0");
        posted.setReviewToken("reviewed");
        filled.setReviewToken("reviewed");
        action.setForm(posted);

        try (var controllers = org.mockito.Mockito.mockConstruction(
                io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.pdf.PDFController.class,
                (controller, ctx) -> {
                    when(controller.getFileName()).thenReturn("bpmh-test.pdf");
                    when(controller.getOutputPath()).thenReturn(generatedPdf.toString());
                })) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(controllers.constructed()).hasSize(1);
        }

        verify(response).reset();
        verify(response).setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setContentType("application/pdf");
        verify(response).setContentLength(pdfBytes.length);
        verify(handler).saveFormHistory();
        assertThat(written.toByteArray()).isEqualTo(pdfBytes);
        assertThat(java.nio.file.Files.exists(generatedPdf)).isFalse();
    }

    @Test
    void shouldDenyAccess_whenFormReadPrivilegeMissingOnFetchOrSavedPrint() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_form"), eq("r"), isNull())).thenReturn(false);
        when(request.getParameter("demographic_no")).thenReturn("1001");
        when(request.getParameter("formId")).thenReturn("0");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_form)");

        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("method")).thenReturn("print");
        BpmhForm2Bean savedHistory = new BpmhForm2Bean();
        savedHistory.setDemographicNo("1001");
        savedHistory.setFormId("9");
        action.setForm(savedHistory);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_form)");
        org.mockito.Mockito.verifyNoInteractions(handler);
    }
}
