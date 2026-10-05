/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.dashboard.admin;

import io.github.carlos_emr.carlos.dashboard.handler.DiseaseRegistryHandler;
import io.github.carlos_emr.carlos.dashboard.handler.MessageHandler;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises request dispatch and the real method guards without constructing unrelated handlers.
 * @since 2026-10-05
 */
@Tag("unit")
class BulkPatientDashboard2ActionUnitTest extends CarlosUnitTestBase {
    private BulkPatientDashboard2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LoggedInInfo user;
    private DiseaseRegistryHandler registry;
    private MessageHandler messages;

    @BeforeEach
    void prepare() {
        // CALLS_REAL_METHODS runs the real action while avoiding handler constructors with static DAOs.
        action = mock(BulkPatientDashboard2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4245");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user), anyString(), anyString(), isNull())).thenReturn(true);
        registry = mock(DiseaseRegistryHandler.class);
        messages = mock(MessageHandler.class);
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        ReflectionTestUtils.setField(action, "dashboardManager", mock(DashboardManager.class));
        ReflectionTestUtils.setField(action, "diseaseRegistryHandler", registry);
        ReflectionTestUtils.setField(action, "messageHandler", messages);
        request.setParameter("patientIds", "101,102");
        request.setParameter("dxUpdateICD9Code", "250");
    }

    @ParameterizedTest
    @ValueSource(strings = {"addToDiseaseRegistry", "excludePatients", "setPatientsInactive"})
    void shouldRejectGet_beforeDispatchCanMutatePatients(String method) {
        request.setMethod("GET");
        request.setParameter("method", method);
        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(registry, messages);
    }

    @ParameterizedTest
    @ValueSource(strings = {"addToDiseaseRegistry", "excludePatients", "setPatientsInactive"})
    void shouldRejectGet_onDirectMutationEntryPoints(String method) throws Exception {
        request.setMethod("GET");
        assertThat(action.getClass().getMethod(method).invoke(action)).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(registry, messages);
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "PoSt", "PO\u017fT", "PUT", "DELETE"})
    void shouldRejectOtherMethodTokens_withoutMutatingPatients(String httpMethod) {
        request.setMethod(httpMethod);
        request.setParameter("method", "addToDiseaseRegistry");
        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(registry, messages);
    }

    @Test
    void shouldPreserveGet_forTheReadOnlyCodeDescription() throws Exception {
        request.setMethod("GET");
        request.setParameter("method", "getICD9Description");
        when(registry.getDescription("250")).thenReturn("Diabetes");
        assertThat(action.execute()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"icd9code\":\"250\"", "\"description\":\"Diabetes\"");
        verify(registry).getDescription("250");
        verifyNoInteractions(messages);
    }

    @Test
    void shouldRegisterOnlySelectedPatients_whenPostIsAuthorized() {
        request.setMethod("POST");
        request.setParameter("method", "addToDiseaseRegistry");
        assertThat(action.execute()).isNull();
        verify(registry).addToDiseaseRegistry(101, "250", "4245");
        verify(registry).addToDiseaseRegistry(102, "250", "4245");
        verifyNoMoreInteractions(registry);
        assertThat(response.getHeader("Allow")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"addToDiseaseRegistry", "excludePatients", "setPatientsInactive"})
    void shouldRetainWritePermissionChecks_forPost(String method) {
        request.setMethod("POST");
        request.setParameter("method", method);
        when(security.hasPrivilege(eq(user), anyString(), anyString(), isNull())).thenReturn(false);
        assertThat(action.execute()).isEqualTo("unauthorized");
        verifyNoInteractions(registry, messages);
    }
    @Test
    void shouldAuditAndNotifyOnlyInsertedPatients_whenAnActiveDiagnosisIsSkipped() {
        request.setMethod("POST");
        when(registry.addToDiseaseRegistry(101, "250", "4245")).thenReturn(501);
        assertThat(action.addToDiseaseRegistry()).isNull();
        logActionMock.verify(() -> LogAction.addLog("4245", LogConst.ADD, "DX", "501", request.getRemoteAddr(), ""));
        logActionMock.verifyNoMoreInteractions();
        verify(messages).notifyProvider(anyString(), argThat(text -> text.contains("[101]") && !text.contains("102")), eq("4245"), isNull());
    }

    @Test
    void shouldNotAuditOrNotify_whenAllDiagnosesAlreadyExist() {
        request.setMethod("POST");
        assertThat(action.addToDiseaseRegistry()).isNull();
        logActionMock.verifyNoInteractions();
        verifyNoInteractions(messages);
    }

}
