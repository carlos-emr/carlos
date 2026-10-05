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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** On-host reference information must not redirect medication names to a third party. */
@Tag("unit")
@Tag("rx")
@DisplayName("Local prescription drug information")
class RxDrugInfo2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LoggedInInfo loggedInInfo;
    private RxDrugData.DrugSearch search;
    private RxDrugData.DrugMonograph monograph;
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loginContext;
    private MockedConstruction<RxDrugData> drugData;
    private RxDrugInfo2Action action;
    private RuntimeException lookupFailure;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        security = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(loggedInInfo, "_rx", "r", null)).thenReturn(true);
        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletContext.when(ServletActionContext::getResponse).thenReturn(response);
        loginContext = mockStatic(LoggedInInfo.class);
        loginContext.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
        search = mock(RxDrugData.DrugSearch.class);
        when(search.getBrand()).thenReturn(new ArrayList<>());
        when(search.getGen()).thenReturn(new ArrayList<>());
        monograph = mock(RxDrugData.DrugMonograph.class);
        when(monograph.getName()).thenReturn("Synthetic ingredient");
        when(monograph.getProduct()).thenReturn("Synthetic product 20 mg");
        drugData = mockConstruction(RxDrugData.class, (data, context) -> {
            when(data.listDrug2(anyString())).thenReturn(search);
            when(data.getDrug2(anyString())).thenReturn(monograph);
            when(data.getDrugByDIN(anyString())).thenReturn(monograph);
            if (lookupFailure != null) {
                when(data.listDrug2(anyString())).thenThrow(lookupFailure);
                when(data.getDrug2(anyString())).thenThrow(lookupFailure);
                when(data.getDrugByDIN(anyString())).thenThrow(lookupFailure);
            }
        });
        action = new RxDrugInfo2Action();
    }

    @AfterEach
    void tearDown() {
        drugData.close();
        loginContext.close();
        servletContext.close();
    }

    private void assertLocalView() {
        assertThat(assertDoesNotThrow(action::execute)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getHeader("Location")).isNull();
    }

    @Test
    void shouldShowLocalReferenceMatches_whenGivenAGenericName() {
        RxDrugData.MinDrug match = mock(RxDrugData.MinDrug.class);
        when(match.getpKey()).thenReturn("1234");
        when(match.getName()).thenReturn("Synthetic ingredient 20 mg");
        when(search.getGen()).thenReturn(new ArrayList<>(List.of(match)));
        request.setParameter("GN", "Synthetic ingredient");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoSearchTerm")).isEqualTo("Synthetic ingredient");
        assertThat(request.getAttribute("drugInfoMatches")).isEqualTo(List.of(match));
        verify(drugData.constructed().getFirst()).listDrug2("Synthetic ingredient");
        verify(security).hasPrivilege(loggedInInfo, "_rx", "r", null);
    }

    @Test
    void shouldShowTheLocalMonograph_whenGivenADrugRefProductId() throws Exception {
        request.setParameter("BN", "1234");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoMonograph")).isSameAs(monograph);
        verify(drugData.constructed().getFirst()).getDrug2("1234");
    }

    @Test
    void shouldPreferTheExactProduct_whenTheGenericDescriptionIsAlsoPresent() throws Exception {
        request.setParameter("GN", "Synthetic ingredient");
        request.setParameter("BN", "1234");
        assertLocalView();
        verify(drugData.constructed().getFirst()).getDrug2("1234");
        verify(drugData.constructed().getFirst(), never()).listDrug2(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", " null "})
    void shouldUseTheProductSelector_whenTheGenericNameIsUnavailable(String missingName) {
        request.setParameter("GN", missingName);
        request.setParameter("BN", "1234");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoMonograph")).isSameAs(monograph);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", " null "})
    void shouldShowAnEmptyLocalView_withoutLookingUpMissingSelectors(String missingName) {
        request.setParameter("GN", missingName);
        request.setParameter("BN", missingName);
        assertLocalView();
        assertThat(drugData.constructed()).isEmpty();
    }

    @Test
    void shouldShowAnEmptyLocalView_whenNoSelectorIsProvided() {
        assertLocalView();
        assertThat(drugData.constructed()).isEmpty();
    }

    @Test
    void shouldShowNoMatchesLocally_whenTheReferenceSearchIsEmpty() {
        request.setParameter("GN", "Unknown synthetic ingredient");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoMatches")).isEqualTo(List.of());
        assertThat(request.getAttribute("drugInfoUnavailable")).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldReportUnavailableLocally_whenTheReferenceSearchFails() {
        search.failed = true;
        request.setParameter("GN", "Synthetic ingredient");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoUnavailable")).isEqualTo(Boolean.TRUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GN", "BN", "DIN"})
    void shouldReportUnavailableLocally_whenTheReferenceServiceThrows(String selector) {
        lookupFailure = new IllegalStateException("Synthetic reference outage");
        request.setParameter(selector, "1234");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoUnavailable")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldReportUnavailableLocally_whenTheReferenceSearchReturnsNull() {
        search = null;
        request.setParameter("GN", "Synthetic ingredient");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoUnavailable")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldNotPresentAnEmptyMonograph_asReferenceInformation() {
        when(monograph.getName()).thenReturn(null);
        when(monograph.getProduct()).thenReturn(null);
        request.setParameter("BN", "1234");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoMonograph")).isNull();
        assertThat(request.getAttribute("drugInfoRequested")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldHandleAMissingProduct_withoutLeavingTheLocalApplication() {
        monograph = null;
        request.setParameter("BN", "1234");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoMonograph")).isNull();
        assertThat(request.getAttribute("drugInfoUnavailable")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void shouldUseTheDin_forAnExactPrescribedProductDespiteADescriptiveGenericName() throws Exception {
        request.setParameter("GN", "Synthetic ingredient 20mg tablet");
        request.setParameter("DIN", "00123456");
        request.setParameter("BN", "different-key");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoMonograph")).isSameAs(monograph);
        assertThat(request.getAttribute("drugInfoSearchTerm")).isEqualTo("Synthetic ingredient 20mg tablet");
        verify(drugData.constructed().getFirst()).getDrugByDIN("00123456");
        verify(drugData.constructed().getFirst(), never()).getDrug2(anyString());
        verify(drugData.constructed().getFirst(), never()).listDrug2(anyString());
    }

    @Test
    void shouldNotSubstituteATextSearch_whenTheExactDinIsUnknown() {
        monograph = null;
        request.setParameter("GN", "Synthetic ingredient");
        request.setParameter("DIN", "00123456");
        assertLocalView();
        assertThat(request.getAttribute("drugInfoMonograph")).isNull();
        verify(drugData.constructed().getFirst(), never()).listDrug2(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", " null "})
    void shouldUseTheAvailableName_whenTheDinIsMissing(String missingDin) {
        request.setParameter("GN", "Synthetic ingredient");
        request.setParameter("DIN", missingDin);
        assertLocalView();
        verify(drugData.constructed().getFirst()).listDrug2("Synthetic ingredient");
    }

    @Test
    void shouldDenyAccessBeforeAnyLookup_whenReadPrivilegeIsMissing() {
        when(security.hasPrivilege(loggedInInfo, "_rx", "r", null)).thenReturn(false);
        request.setParameter("GN", "Synthetic ingredient");
        request.setParameter("BN", "1234");
        request.setParameter("DIN", "00123456");
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        assertThat(drugData.constructed()).isEmpty();
        assertThat(response.getRedirectedUrl()).isNull();
    }
}
