/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.decisionSupport.web;

import io.github.carlos_emr.carlos.decisionSupport.model.DSGuideline;
import io.github.carlos_emr.carlos.decisionSupport.service.DSService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DSGuideline2ActionUnitTest extends CarlosUnitTestBase {
    private DSGuideline2Action action;
    private MockHttpServletRequest request;
    private DSService service;
    private SecurityInfoManager security;
    private LoggedInInfo user;

    @BeforeEach
    void prepare() {
        action = mock(DSGuideline2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest();
        action.request = request;
        action.response = new MockHttpServletResponse();
        user = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        service = mock(DSService.class);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(user, "_demographic", "r", null)).thenReturn(true);
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(true);
        ReflectionTestUtils.setField(action, "dsService", service);
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        request.setParameter("provider_no", "42");
    }

    @Test
    void shouldEvaluateListWithLoggedInContext_whenPatientIsSelected() throws Exception {
        var passed = mock(DSGuideline.class);
        var failed = mock(DSGuideline.class);
        when(passed.getId()).thenReturn(7);
        when(failed.getId()).thenReturn(8);
        when(passed.evaluateBoolean(user, "101")).thenReturn(true);
        when(failed.evaluateBoolean(user, "101")).thenReturn(false);
        var guidelines = List.of(passed, failed);
        when(service.getDsGuidelinesByProvider("42")).thenReturn(guidelines);
        request.setParameter("demographic_no", "101");

        assertEquals("guidelineList", action.execute());

        assertSame(guidelines, request.getAttribute("guidelines"));
        assertEquals(Map.of(7, true, 8, false), request.getAttribute("guidelineResults"));
        verify(passed).evaluateBoolean(user, "101");
        verify(failed).evaluateBoolean(user, "101");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void shouldReturnMetadataWithoutEvaluation_whenNoPatientIsSelected(String patient) throws Exception {
        var guideline = mock(DSGuideline.class);
        when(service.getDsGuidelinesByProvider("42")).thenReturn(List.of(guideline));
        if (patient != null) request.setParameter("demographic_no", patient);

        assertEquals("guidelineList", action.execute());

        assertEquals(Map.of(), request.getAttribute("guidelineResults"));
        verifyNoInteractions(guideline);
    }

    @Test
    void shouldReturnEmptyList_whenProviderIsAbsent() throws Exception {
        request.removeParameter("provider_no");
        request.setParameter("demographic_no", "101");

        assertEquals("guidelineList", action.execute());

        assertEquals(List.of(), request.getAttribute("guidelines"));
        assertEquals(Map.of(), request.getAttribute("guidelineResults"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRefuseEvaluation_whenReadPermissionIsMissing() {
        when(security.hasPrivilege(user, "_demographic", "r", null)).thenReturn(false);
        request.setParameter("demographic_no", "101");

        assertThrows(SecurityException.class, () -> action.execute());

        verifyNoInteractions(service);
    }

    @Test
    void shouldRefuseEvaluation_whenChartPermissionIsMissing() {
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(false);
        request.setParameter("demographic_no", "101");

        assertThrows(SecurityException.class, () -> action.execute());

        verifyNoInteractions(service);
    }
}
