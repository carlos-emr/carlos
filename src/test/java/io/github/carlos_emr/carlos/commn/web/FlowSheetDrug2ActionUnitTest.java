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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.FlowSheetDrugDao;
import io.github.carlos_emr.carlos.commn.dao.FlowSheetDxDao;
import io.github.carlos_emr.carlos.commn.model.FlowSheetDrug;
import io.github.carlos_emr.carlos.commn.model.FlowSheetDx;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The flowsheet's left panel links a diagnosis ({@code method=dxSave}) or a current drug ({@code method=save}) to
 * the flowsheet (issue #4974): the link is saved once, the answer is a redirect back to the flowsheet, and the page
 * shows a linked drug as linked instead of offering it again.
 *
 * @since 2026-10-18
 */
@DisplayName("FlowSheetDrug2Action - flowsheet diagnosis and drug links")
@Tag("unit")
@Tag("clinical")
class FlowSheetDrug2ActionUnitTest extends CarlosWebTestBase {

    private static final String FLOWSHEET_URL = "/carlos/encounter/oscarMeasurements/ViewTemplateFlowSheet";

    private FlowSheetDrugDao drugDao;
    private FlowSheetDxDao dxDao;

    @BeforeEach
    void prepare() {
        drugDao = mock(FlowSheetDrugDao.class);
        dxDao = mock(FlowSheetDxDao.class);
        replaceSpringUtilsBean(FlowSheetDrugDao.class, drugDao);
        replaceSpringUtilsBean(FlowSheetDxDao.class, dxDao);
        when(drugDao.getFlowSheetDrugs(any(), any())).thenReturn(List.of());
        when(dxDao.getFlowSheetDxMap(any(), any())).thenReturn(new HashMap<>());
        mockRequest.setMethod("POST");
        mockRequest.setContextPath("/carlos");
        mockSession.setAttribute("user", "999998");
        addRequestParameter("flowsheet", "diab2");
        addRequestParameter("demographic", "10");
    }

    private void linkDx() {
        addRequestParameter("method", "dxSave");
        addRequestParameter("dxCode", "250");
        addRequestParameter("dxCodeType", "icd9");
    }

    private void linkDrug() {
        addRequestParameter("method", "save");
        addRequestParameter("atcCode", "C10AA05");
    }

    @Test
    @DisplayName("should save the diagnosis link and redirect to the flowsheet")
    void shouldRedirectToFlowsheet_whenDiagnosisLinked() throws Exception {
        linkDx();

        String result = executeAction(new FlowSheetDrug2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getRedirectedUrl()).isEqualTo(FLOWSHEET_URL + "?demographic_no=10&template=diab2");
        ArgumentCaptor<FlowSheetDx> saved = ArgumentCaptor.forClass(FlowSheetDx.class);
        verify(dxDao).persist(saved.capture());
        assertThat(saved.getValue().getFlowsheet()).isEqualTo("diab2");
        assertThat(saved.getValue().getDemographicNo()).isEqualTo(10);
        assertThat(saved.getValue().getDxCode()).isEqualTo("250");
        assertThat(saved.getValue().getDxCodeType()).isEqualTo("icd9");
        assertThat(saved.getValue().getProviderNo()).isEqualTo("999998");
        verify(mockSecurityInfoManager).hasPrivilege(any(), eq("_demographic"), eq("w"), eq("10"));
    }

    @Test
    @DisplayName("should save the drug link and redirect to the flowsheet")
    void shouldRedirectToFlowsheet_whenDrugLinked() throws Exception {
        linkDrug();

        String result = executeAction(new FlowSheetDrug2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getRedirectedUrl()).isEqualTo(FLOWSHEET_URL + "?demographic_no=10&template=diab2");
        ArgumentCaptor<FlowSheetDrug> saved = ArgumentCaptor.forClass(FlowSheetDrug.class);
        verify(drugDao).persist(saved.capture());
        assertThat(saved.getValue().getFlowsheet()).isEqualTo("diab2");
        assertThat(saved.getValue().getDemographicNo()).isEqualTo(10);
        assertThat(saved.getValue().getAtcCode()).isEqualTo("C10AA05");
        assertThat(saved.getValue().getProviderNo()).isEqualTo("999998");
    }

    @Test
    @DisplayName("should encode the flowsheet name in the redirect")
    void shouldEncodeTemplate_inRedirect() throws Exception {
        addRequestParameter("flowsheet", "my sheet&x=1");
        linkDrug();

        executeAction(new FlowSheetDrug2Action());

        assertThat(mockResponse.getRedirectedUrl())
                .isEqualTo(FLOWSHEET_URL + "?demographic_no=10&template=my+sheet%26x%3D1");
    }

    @Test
    @DisplayName("should not save a diagnosis the flowsheet already links, and still return to the flowsheet")
    void shouldNotSaveDiagnosisAgain_whenFlowsheetAlreadyLinksIt() throws Exception {
        HashMap<String, String> linked = new HashMap<>();
        linked.put("icd9250", "999998");
        when(dxDao.getFlowSheetDxMap("diab2", 10)).thenReturn(linked);
        linkDx();

        String result = executeAction(new FlowSheetDrug2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getRedirectedUrl()).isEqualTo(FLOWSHEET_URL + "?demographic_no=10&template=diab2");
        verify(dxDao, never()).persist(any());
    }

    @Test
    @DisplayName("should not save a drug the flowsheet already links, and still return to the flowsheet")
    void shouldNotSaveDrugAgain_whenFlowsheetAlreadyLinksIt() throws Exception {
        FlowSheetDrug existing = new FlowSheetDrug();
        existing.setAtcCode("C10AA05");
        when(drugDao.getFlowSheetDrugs("diab2", 10)).thenReturn(List.of(existing));
        linkDrug();

        String result = executeAction(new FlowSheetDrug2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getRedirectedUrl()).isEqualTo(FLOWSHEET_URL + "?demographic_no=10&template=diab2");
        verify(drugDao, never()).persist(any());
    }

    @Test
    @DisplayName("should save a drug whose ATC code the flowsheet doesn't link yet")
    void shouldSaveDrug_whenFlowsheetLinksOnlyAnotherDrug() throws Exception {
        FlowSheetDrug other = new FlowSheetDrug();
        other.setAtcCode("A10BA02");
        when(drugDao.getFlowSheetDrugs("diab2", 10)).thenReturn(List.of(other));
        linkDrug();

        executeAction(new FlowSheetDrug2Action());

        verify(drugDao).persist(any(FlowSheetDrug.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "get", "PUT"})
    @DisplayName("should answer 405 and link nothing when the request is not a POST")
    void shouldReturn405_whenRequestIsNotPost(String httpMethod) throws Exception {
        mockRequest.setMethod(httpMethod);
        linkDx();
        addRequestParameter("atcCode", "C10AA05");

        String result = executeAction(new FlowSheetDrug2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(drugDao, dxDao);
    }

    @Test
    @DisplayName("should refuse a user without write rights for the patient before anything else")
    void shouldThrowSecurityException_whenPatientWriteMissing() {
        denyPrivilege("_demographic", "w");
        linkDx();

        assertThatThrownBy(() -> executeAction(new FlowSheetDrug2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");
        verifyNoInteractions(drugDao, dxDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "0", "-1", "10x", "1234567890"})
    @DisplayName("should answer 400 and link nothing for a patient number the flowsheet never posts")
    void shouldReturn400_whenPatientNumberMalformed(String demographic) throws Exception {
        addRequestParameter("demographic", demographic);
        linkDx();

        String result = executeAction(new FlowSheetDrug2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(drugDao, dxDao);
    }

    @Test
    @DisplayName("should answer 400 and link nothing without a flowsheet name")
    void shouldReturn400_whenFlowsheetMissing() throws Exception {
        addRequestParameter("flowsheet", " ");
        linkDrug();

        executeAction(new FlowSheetDrug2Action());

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(drugDao, dxDao);
    }

    @Test
    @DisplayName("should answer 400 and link nothing for a drug without an ATC code")
    void shouldReturn400_whenAtcCodeBlank() throws Exception {
        addRequestParameter("method", "save");
        addRequestParameter("atcCode", "");

        executeAction(new FlowSheetDrug2Action());

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(drugDao, never()).persist(any());
    }

    @Test
    @DisplayName("should answer 400 and link nothing for a diagnosis without its coding system")
    void shouldReturn400_whenDiagnosisCodingSystemMissing() throws Exception {
        addRequestParameter("method", "dxSave");
        addRequestParameter("dxCode", "250");

        executeAction(new FlowSheetDrug2Action());

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(dxDao, never()).persist(any());
    }

    @Test
    @DisplayName("should answer 400 and link nothing for an unknown method")
    void shouldReturn400_whenMethodUnknown() throws Exception {
        addRequestParameter("method", "delete");
        addRequestParameter("atcCode", "C10AA05");

        executeAction(new FlowSheetDrug2Action());

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(drugDao, dxDao);
    }

    @Test
    @DisplayName("should show a linked drug as linked on the flowsheet instead of offering it again")
    void shouldShowLinkedDrugAsLinked_onFlowsheetPage() throws Exception {
        String page = Files.readString(resolveProjectPath(Path.of("src", "main", "webapp", "WEB-INF", "jsp",
                "encounter", "oscarMeasurements", "TemplateFlowSheetPage.jspf")), StandardCharsets.UTF_8);
        int listing = page.indexOf("id=\"rxFullListing\"");
        int linkedCodes = page.indexOf("flowSheetDrugDao.getFlowSheetDrugs(temp, Integer.parseInt(demographic_no))",
                listing);
        int loop = page.indexOf("for (int i = 0; i < arr.length; i++){", listing);
        int linkedBranch = page.indexOf("linkedAtcCodes.contains(rxAtc)", loop);
        int blankBranch = page.indexOf("rxAtc == null || rxAtc.isBlank()", linkedBranch);
        int offerBranch = page.indexOf("<% } else { %>", blankBranch);
        int saveForm = page.indexOf("<input type=\"hidden\" name=\"method\" value=\"save\"/>", listing);
        int listEnd = page.indexOf("</ul>", loop);

        assertThat(listing).isPositive();
        assertThat(linkedCodes).as("linked ATC codes are read before the drug list").isBetween(listing, loop);
        assertThat(linkedBranch).as("a linked drug is shown before any link is offered").isBetween(loop, saveForm);
        assertThat(blankBranch).as("a drug without an ATC code is not offered").isBetween(linkedBranch, saveForm);
        assertThat(offerBranch).as("the link form is offered only in the last branch").isBetween(blankBranch, saveForm);
        assertThat(saveForm).isLessThan(listEnd);
        assertThat(page.substring(linkedBranch, blankBranch)).contains("background-color: yellow;");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth <= 8 && current != null; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate project file: " + relativePath);
    }
}
