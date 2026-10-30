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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the server-side rendering contract of {@code messenger/Doc2PDF} (carlos-emr/carlos#4133):
 * the request names a patient and item keys, the server chooses the route that renders each
 * item, and no request-supplied HTML can reach the PDF. Attached PDFs are staged for the compose
 * window that opened the chooser only, with the patient they were rendered for (issue #4673).
 */
@DisplayName("MsgAttachPDF2Action")
@Tag("unit")
@Tag("messenger")
@Tag("security")
class MsgAttachPDF2ActionUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 4133;
    private static final String PROVIDER = "999998";
    /** A token that must never reach a PDF or a log line. */
    private static final String FORGED_MARKUP = "<script>alert('FAKE-forged')</script>";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<RxSessionBeanResolver> rxResolverMock;

    private SecurityInfoManager securityInfoManager;
    private DemographicManager demographicManager;
    private EChartDao eChartDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    /** The login's message bean: attachments must never be put on it any more. */
    private MsgSessionBean bean;
    /** The key of the compose window the chooser was opened from. */
    private String key;

    /** Routes the action asked to render, in order. */
    private final List<String> renderedRoutes = new ArrayList<>();
    /** HTML handed to the PDF converter, in order. */
    private final List<String> convertedHtml = new ArrayList<>();
    private boolean renderFails;
    /** What the converter returns for every rendered item. */
    private String base64Pdf = "JVBERi0xLjQ=";

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        demographicManager = mock(DemographicManager.class);
        eChartDao = mock(EChartDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER);

        request = new MockHttpServletRequest("POST", "/carlos/messenger/Doc2PDF");
        response = new MockHttpServletResponse();
        bean = new MsgSessionBean();
        request.getSession().setAttribute("msgSessionBean", bean);
        key = MsgAttachmentStaging.newKey();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        rxResolverMock = mockStatic(RxSessionBeanResolver.class);

        allow("_msg", "w");
        allow("_demographic", "r");
        allow("_eChart", "r");
        allow("_rx", "r");
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, PATIENT)).thenReturn(true);

        Demographic patient = new Demographic();
        patient.setLastName("FAKE-Doe");
        patient.setFirstName("Pat");
        when(demographicManager.getDemographic(loggedInInfo, String.valueOf(PATIENT))).thenReturn(patient);

        EChart chart = new EChart();
        chart.setId(77);
        chart.setTimestamp(new Date(0));
        when(eChartDao.getLatestChart(PATIENT)).thenReturn(chart);
    }

    @AfterEach
    void tearDown() {
        rxResolverMock.close();
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private void allow(String object, String privilege) {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(object), eq(privilege), isNull()))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(object), eq(privilege), eq(PATIENT)))
                .thenReturn(true);
    }

    private void deny(String object, String privilege) {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(object), eq(privilege), isNull()))
                .thenReturn(false);
    }

    /** Global read stays; only this patient is restricted for the module. */
    private void denyForPatient(String object) {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(object), eq("r"), eq(PATIENT)))
                .thenReturn(false);
    }

    private MsgAttachPDF2Action newAction() {
        MsgAttachPDF2Action.RouteRenderer renderer = (req, res, route) -> {
            renderedRoutes.add(route);
            if (renderFails) {
                throw new ServletException("FAKE render failure");
            }
            return "<html><body>rendered " + route + "</body></html>";
        };
        MsgAttachPDF2Action.PdfConverter converter = new MsgAttachPDF2Action.PdfConverter() {
            @Override
            public void streamPdf(HttpServletRequest req, HttpServletResponse res, String html) {
                convertedHtml.add(html);
                res.setContentType("application/pdf");
            }

            @Override
            public String toBase64Pdf(HttpServletRequest req, HttpServletResponse res, String html) {
                convertedHtml.add(html);
                return base64Pdf;
            }
        };
        MsgAttachPDF2Action action = new MsgAttachPDF2Action(securityInfoManager, demographicManager,
                new MsgPdfAttachmentResolver(eChartDao), renderer, converter);
        action.setDemographic_no(String.valueOf(PATIENT));
        action.setAttachmentKey(key);
        return action;
    }

    /** The chart PDFs staged for this test's compose window, or null. */
    private String stagedPdfs() {
        MsgAttachmentStaging.Staged staged = MsgAttachmentStaging.peek(request.getSession(), key);
        return staged == null ? null : staged.pdfAttachment();
    }

    /** Stages one earlier chart PDF for this test's compose window. */
    private void givenEarlierAttachment() {
        MsgAttachmentStaging.stagePdfs(request.getSession(), key, PATIENT, pdfRecord("FAKE earlier attachment"));
    }

    private static String pdfRecord(String title) {
        MsgSessionBean records = new MsgSessionBean();
        records.setAppendPDFAttachment("JVBERi0xLjQ=", title);
        return records.getPDFAttachment();
    }

    @Nested
    @DisplayName("request gate")
    class RequestGate {

        @Test
        @DisplayName("should refuse without _msg write")
        void shouldThrowSecurityException_whenMsgWriteDenied() {
            deny("_msg", "w");
            MsgAttachPDF2Action action = newAction();

            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class).hasMessageContaining("_msg");
            assertThat(renderedRoutes).isEmpty();
        }

        @Test
        @DisplayName("should answer GET with 405 before rendering anything")
        void shouldReturn405_whenMethodIsGet() throws Exception {
            request.setMethod("GET");
            MsgAttachPDF2Action action = newAction();
            action.setIsPreview(true);
            action.setPreviewItem("demographic");

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(renderedRoutes).isEmpty();
        }

        @Test
        @DisplayName("should answer 400 when the patient number is missing or malformed")
        void shouldReturn400_whenDemographicNoInvalid() throws Exception {
            MsgAttachPDF2Action action = newAction();
            action.setDemographic_no("1 OR 1=1");

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(renderedRoutes).isEmpty();
        }

        @Test
        @DisplayName("should refuse a patient the caller may not open")
        void shouldThrowSecurityException_whenPatientAccessDenied() {
            when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, PATIENT)).thenReturn(false);
            MsgAttachPDF2Action action = newAction();
            action.setIsPreview(true);
            action.setPreviewItem("demographic");

            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
            assertThat(renderedRoutes).isEmpty();
        }
    }

    @Nested
    @DisplayName("preview")
    class Preview {

        @Test
        @DisplayName("should render the server-chosen demographic route and stream it as a PDF")
        void shouldStreamServerRenderedPdf_forDemographicItem() throws Exception {
            // A legacy client (or a forger) may still post srcText; it is not a parameter of the
            // action any more, so it cannot reach the converter.
            request.addParameter("srcText", FORGED_MARKUP);
            MsgAttachPDF2Action action = newAction();
            action.setIsPreview(true);
            action.setPreviewItem("demographic");

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

            assertThat(renderedRoutes).containsExactly("/demographic/DemographicPdfLabel?demographic_no=" + PATIENT);
            assertThat(convertedHtml).singleElement().asString()
                    .contains("rendered /demographic/DemographicPdfLabel")
                    .doesNotContain(FORGED_MARKUP);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(stagedPdfs()).as("preview never attaches").isNull();
        }

        @Test
        @DisplayName("should render the latest encounter of the named patient")
        void shouldRenderLatestChart_forEncounterItem() throws Exception {
            MsgAttachPDF2Action action = newAction();
            action.setIsPreview(true);
            action.setPreviewItem("encounter");

            action.execute();

            assertThat(renderedRoutes)
                    .containsExactly("/encounter/ViewEcharthistoryprint?echartid=77&demographic_no=" + PATIENT);
        }

        @Test
        @DisplayName("should answer 400 for an item key the chooser never offers")
        void shouldReturn400_whenPreviewItemUnknown() throws Exception {
            MsgAttachPDF2Action action = newAction();
            action.setIsPreview(true);
            action.setPreviewItem("/admin/../security");

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(renderedRoutes).isEmpty();
        }

        @Test
        @DisplayName("should refuse an item whose module the caller cannot read, before including it")
        void shouldThrowSecurityException_whenItemModuleDenied() {
            deny("_rx", "r");
            MsgAttachPDF2Action action = newAction();
            action.setIsPreview(true);
            action.setPreviewItem("prescriptions");

            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class).hasMessageContaining("_rx");
            assertThat(renderedRoutes).isEmpty();
        }

        @Test
        @DisplayName("should refuse an encounter preview without _eChart read before looking the chart up")
        void shouldNotLookUpChart_whenEncounterReadDenied() {
            deny("_eChart", "r");
            MsgAttachPDF2Action action = newAction();
            action.setIsPreview(true);
            action.setPreviewItem("encounter");

            // Same refusal whether or not a chart exists: the lookup never runs, so the response
            // cannot reveal the encounter's existence.
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class).hasMessageContaining("_eChart");
            verify(eChartDao, never()).getLatestChart(anyInt());
            assertThat(renderedRoutes).isEmpty();
        }

        @Test
        @DisplayName("should answer 500 rather than a PDF of an error when the item fails to render")
        void shouldReturn500_whenRenderFails() throws Exception {
            renderFails = true;
            MsgAttachPDF2Action action = newAction();
            action.setIsPreview(true);
            action.setPreviewItem("demographic");

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(convertedHtml).isEmpty();
        }
    }

    @Nested
    @DisplayName("attach")
    class Attach {

        @Test
        @DisplayName("should store one PDF per ticked item with server-computed titles, in chooser order")
        void shouldAttachEachTickedItem_withServerTitles() throws Exception {
            givenEarlierAttachment();
            MsgAttachPDF2Action action = newAction();
            // Out of order, duplicated, and with a key the chooser never offers.
            action.setItem(new String[]{"prescriptions", "demographic", "prescriptions", "srcText"});

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

            assertThat(renderedRoutes).containsExactly(
                    "/demographic/DemographicPdfLabel?demographic_no=" + PATIENT,
                    "/rx/ViewPrintDrugProfile2?demographic_no=" + PATIENT);
            String stored = stagedPdfs();
            assertThat(stored)
                    .doesNotContain("FAKE earlier attachment")
                    .contains("<TITLE>FAKE-Doe, Pat Information</TITLE>")
                    .containsPattern("(?i)<TITLE>Current prescriptions</TITLE>");
            assertThat(stored.split("<STATUS>OK</STATUS>", -1)).hasSize(3);
            rxResolverMock.verify(() -> RxSessionBeanResolver.ensure(request, PATIENT, PROVIDER));
        }

        @Test
        @DisplayName("should title stored PDFs in the first Accept-Language preference that has a bundle")
        void shouldUseNegotiatedLocale_forServerTitles() throws Exception {
            // German has no bundle; the second preference, French, must win over the server locale.
            request.addHeader("Accept-Language", "de-DE, fr;q=0.8");
            // Spelled out rather than left to the mock's parsing of the header.
            request.setPreferredLocales(List.of(Locale.GERMANY, Locale.FRENCH));
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"prescriptions"});

            action.execute();

            assertThat(stagedPdfs()).contains("<TITLE>Ordonnances en cours</TITLE>");
        }

        @Test
        @DisplayName("should attach prescriptions without _demographic read, never looking the patient up")
        void shouldAttachPrescriptions_withoutDemographicRead() throws Exception {
            // DemographicManager.getDemographic enforces _demographic read; only the demographic
            // item's title needs the patient's name, so the other items must not depend on it.
            deny("_demographic", "r");
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"prescriptions"});

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

            assertThat(renderedRoutes).containsExactly("/rx/ViewPrintDrugProfile2?demographic_no=" + PATIENT);
            verify(demographicManager, never()).getDemographic(any(LoggedInInfo.class), anyString());
        }

        @Test
        @DisplayName("should answer 404 and attach nothing when the demographic item's patient does not exist")
        void shouldReturn404_whenDemographicItemPatientMissing() throws Exception {
            givenEarlierAttachment();
            when(demographicManager.getDemographic(loggedInInfo, String.valueOf(PATIENT))).thenReturn(null);
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic", "prescriptions"});

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            assertThat(renderedRoutes).isEmpty();
            assertThat(stagedPdfs()).contains("FAKE earlier attachment");
        }

        @Test
        @DisplayName("should number the stored PDFs 0, 1, ... and keep them off the login's message bean")
        void shouldAssignDistinctFileIds_whenAttachingSeveralItems() throws Exception {
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic", "encounter", "prescriptions"});

            action.execute();

            assertThat(stagedPdfs())
                    .contains("<FILE_ID>0</FILE_ID>", "<FILE_ID>1</FILE_ID>", "<FILE_ID>2</FILE_ID>");
            assertThat(bean.getPDFAttachment()).isNull();
            assertThat(bean.getAttachment()).isNull();
        }

        @Test
        @DisplayName("should refuse chart PDFs that are larger than a message can hold, and keep what was attached before")
        void shouldReturn413AndKeepExistingAttachments_whenPdfsAreTooLargeForAMessage() throws Exception {
            givenEarlierAttachment();
            // Two PDFs of about 40 KB, as the renderer makes them with their fonts, are 107 KB as base64.
            base64Pdf = "A".repeat(53_000);
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic", "prescriptions"});

            assertThat(action.execute()).isEqualTo("none");

            assertThat(response.getStatus()).isEqualTo(413);
            assertThat(response.getErrorMessage()).isEqualTo(MsgAttachPDF2Action.PDFS_TOO_LARGE);
            assertThat(stagedPdfs()).contains("FAKE earlier attachment").doesNotContain("AAAA");
        }

        @Test
        @DisplayName("should attach one PDF of about 40 KB, which fits a message, and stage it whole")
        void shouldStageWhole_whenOnePdfFitsAMessage() throws Exception {
            base64Pdf = "A".repeat(55_000);
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic"});

            assertThat(action.execute()).isEqualTo("success");

            assertThat(stagedPdfs()).contains("A".repeat(55_000));
            assertThat(MessageTbl.pdfAttachmentFits(stagedPdfs())).isTrue();
        }

        @Test
        @DisplayName("should refuse an item whose module is restricted for this patient, before rendering")
        void shouldThrowSecurityException_whenItemRestrictedForPatient() {
            givenEarlierAttachment();
            denyForPatient("_rx");
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic", "prescriptions"});

            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class).hasMessageContaining("_rx");
            assertThat(renderedRoutes).isEmpty();
            assertThat(stagedPdfs()).contains("FAKE earlier attachment");
        }

        @Test
        @DisplayName("should keep the existing PDFs when building the replacement fails part-way")
        void shouldKeepExistingAttachments_whenReplacementFailsMidway() {
            givenEarlierAttachment();
            when(eChartDao.getLatestChart(PATIENT)).thenThrow(new IllegalStateException("FAKE lookup failure"));
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic", "encounter"});

            assertThatThrownBy(action::execute).isInstanceOf(IllegalStateException.class);
            assertThat(stagedPdfs()).contains("FAKE earlier attachment");
        }

        @Test
        @DisplayName("should keep a non-PDF attachment on the message when replacing the chart PDFs")
        void shouldKeepTransferredAttachment_whenAttachingChartPdfs() throws Exception {
            MsgAttachmentStaging.stageItems(request.getSession(), key, "<root><table name=\"FAKE\"/></root>");
            givenEarlierAttachment();
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic"});

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

            MsgAttachmentStaging.Staged staged = MsgAttachmentStaging.peek(request.getSession(), key);
            assertThat(staged.itemAttachment()).isEqualTo("<root><table name=\"FAKE\"/></root>");
            assertThat(staged.pdfAttachment()).doesNotContain("FAKE earlier attachment").contains("Information");
        }

        @Test
        @DisplayName("should leave existing attachments untouched when one ticked item is refused")
        void shouldKeepExistingAttachments_whenItemPrivilegeDenied() {
            givenEarlierAttachment();
            deny("_eChart", "r");
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic", "encounter"});

            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class).hasMessageContaining("_eChart");
            assertThat(renderedRoutes).isEmpty();
            assertThat(stagedPdfs()).contains("FAKE earlier attachment");
        }

        @Test
        @DisplayName("should record a failed render as a BAD entry instead of dropping it silently")
        void shouldStoreBadEntry_whenRenderFails() throws Exception {
            renderFails = true;
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic"});

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(stagedPdfs())
                    .contains("<STATUS>BAD</STATUS>")
                    .contains("(N/A)")
                    .contains("<CONTENT></CONTENT>");
        }

        @Test
        @DisplayName("should skip the encounter item when the patient has no encounter record")
        void shouldSkipEncounter_whenPatientHasNoChart() throws Exception {
            when(eChartDao.getLatestChart(anyInt())).thenReturn(null);
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"encounter"});

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(renderedRoutes).isEmpty();
            assertThat(stagedPdfs()).isNull();
        }

        @Test
        @DisplayName("should clear the chart attachments when Attach is pressed with nothing ticked")
        void shouldClearAttachments_whenNothingTicked() throws Exception {
            givenEarlierAttachment();
            MsgAttachPDF2Action action = newAction();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(stagedPdfs()).isNull();
            assertThat(MsgAttachmentStaging.peek(request.getSession(), key)).isNull();
            assertThat(renderedRoutes).isEmpty();
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.NullAndEmptySource
        @org.junit.jupiter.params.provider.ValueSource(strings = {"not-a-key", "AAAAAAAAAAAAAAAAAAAAA!"})
        @DisplayName("should answer 400 when the chooser names no compose window")
        void shouldReturn400_whenComposeKeyMissingOrMalformed(String composeKey) throws Exception {
            MsgAttachPDF2Action action = newAction();
            action.setAttachmentKey(composeKey);
            action.setItem(new String[]{"demographic"});

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(renderedRoutes).isEmpty();
        }

        @Test
        @DisplayName("should stage the PDFs for the patient they were rendered for")
        void shouldRecordRenderedPatient_whenAttaching() throws Exception {
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic"});

            action.execute();

            assertThat(MsgAttachmentStaging.peek(request.getSession(), key).pdfDemographicNo()).isEqualTo(PATIENT);
        }

        @Test
        @DisplayName("should leave another compose window's attachments alone")
        void shouldKeepOtherWindowsAttachments_whenAttaching() throws Exception {
            // Window B was opened for another patient and has attached that patient's chart.
            String otherWindow = MsgAttachmentStaging.newKey();
            MsgAttachmentStaging.stagePdfs(request.getSession(), otherWindow, 5000, pdfRecord("FAKE other patient"));
            MsgAttachPDF2Action action = newAction();
            action.setItem(new String[]{"demographic"});

            action.execute();

            MsgAttachmentStaging.Staged other = MsgAttachmentStaging.peek(request.getSession(), otherWindow);
            assertThat(other.pdfDemographicNo()).isEqualTo(5000);
            assertThat(other.pdfAttachment()).contains("FAKE other patient").doesNotContain("Information");
            assertThat(stagedPdfs()).contains("Information").doesNotContain("FAKE other patient");
        }

        @Test
        @DisplayName("should log neither the patient nor the rendered content when an item fails")
        void shouldNotLogPhi_whenRenderFails() throws Exception {
            renderFails = true;
            try (LogCapture logs = LogCapture.forLogger(MsgAttachPDF2Action.class)) {
                MsgAttachPDF2Action action = newAction();
                action.setItem(new String[]{"demographic"});
                action.execute();

                assertThat(logs.messages())
                        .isNotEmpty()
                        .noneMatch(message -> message.contains(String.valueOf(PATIENT)))
                        .noneMatch(message -> message.contains("FAKE-Doe"));
            }
        }
    }

    @Test
    @DisplayName("should present the include as a GET of the route carrying only the route's parameters")
    void shouldPresentIncludeAsGetOfRoute_forItemRoutes() {
        request.addParameter("item", "demographic");
        request.addParameter("srcText", FORGED_MARKUP);
        request.addParameter("demographic_no", "999");

        HttpServletRequest view = new MsgAttachPDF2Action.IncludedViewRequest(request,
                "/encounter/ViewEcharthistoryprint?echartid=77&demographic_no=" + PATIENT);

        assertThat(view.getMethod()).isEqualTo("GET");
        assertThat(view.getParameterMap()).containsOnlyKeys("echartid", "demographic_no");
        assertThat(view.getParameter("demographic_no")).isEqualTo(String.valueOf(PATIENT));
        assertThat(view.getParameterValues("demographic_no")).containsExactly(String.valueOf(PATIENT));
        assertThat(view.getParameter("srcText")).isNull();
        assertThat(view.getParameterValues("item")).isNull();
        assertThat(java.util.Collections.list(view.getParameterNames())).containsExactly("echartid", "demographic_no");
        assertThat(view.getSession()).as("the session (and so the login) is the caller's").isSameAs(request.getSession());
    }

    @Test
    @DisplayName("should never expose a setter for client-supplied HTML")
    void shouldNotBindSrcText_forTypeContract() {
        assertThat(MsgAttachPDF2Action.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("setSrcText", "setAttachmentTitle", "setAttachmentCount")
                .contains("setItem", "setPreviewItem", "setDemographic_no", "setAttachmentKey");
    }

    @Nested
    @DisplayName("chooser item rule")
    class ChooserItemRule {

        @Test
        @DisplayName("should offer an item only with its module read both globally and for the patient")
        void shouldRequireGlobalAndPatientRead_forChooserItem() {
            allow("_eChart", "r");
            assertThat(MsgAttachPDF2Action.canReadItem(securityInfoManager, loggedInInfo,
                    MsgPdfAttachmentResolver.Item.ENCOUNTER, PATIENT)).isTrue();

            denyForPatient("_eChart");
            assertThat(MsgAttachPDF2Action.canReadItem(securityInfoManager, loggedInInfo,
                    MsgPdfAttachmentResolver.Item.ENCOUNTER, PATIENT)).isFalse();

            deny("_rx", "r");
            assertThat(MsgAttachPDF2Action.canReadItem(securityInfoManager, loggedInInfo,
                    MsgPdfAttachmentResolver.Item.PRESCRIPTIONS, PATIENT)).isFalse();
        }
    }
}
