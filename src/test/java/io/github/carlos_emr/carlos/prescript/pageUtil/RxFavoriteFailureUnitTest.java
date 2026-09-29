/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Favorite failures must not be reported as successful prescription staging or editing.
 * @since 2026-09-27
 */
@Tag("unit")
@Tag("prescription")
class RxFavoriteFailureUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final RxSessionBean bean = mock(RxSessionBean.class);
    private final LoggedInInfo login = mock(LoggedInInfo.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loginContext;

    @BeforeEach
    void setUpFavoriteRequest() {
        registerMock(SecurityInfoManager.class, security);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loginContext = mockStatic(LoggedInInfo.class);
        loginContext.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        when(security.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        when(security.hasPrivilege(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(bean.getProviderNo()).thenReturn("999998");
        when(bean.getDemographicNo()).thenReturn(1);
        request.setParameter("demographicNo", "1");
        request.setParameter("repeat", "0");
        request.setMethod("POST");
        request.setParameter("favoriteId", "42");
        request.setParameter("randomId", "77");
        RxSessionBeanResolver.register(request.getSession(), bean);
    }

    @AfterEach
    void closeFavoriteRequest() {
        loginContext.close();
        servlet.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectEditWithoutLookup_whenPrescriptionUpdatePrivilegeIsMissing(boolean ajax) {
        when(security.hasPrivilege(login, "_rx", "u", null)).thenReturn(false);
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            var action = new RxUpdateFavorite2Action();
            action.setFavoriteId("42");
            action.setRepeat("0");

            assertThatThrownBy(() -> {
                if (ajax) action.ajaxEditFavorite();
                else action.execute();
            }).isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_rx)");
            assertThat(data.constructed()).isEmpty();
            verify(bean, org.mockito.Mockito.never()).addStashItem(any(), any());
        }
    }

    private String useFavorite(boolean ajax, String id) throws Exception {
        RxUseFavorite2Action action = new RxUseFavorite2Action();
        action.setFavoriteId(id);
        request.setParameter("favoriteId", id);
        return ajax ? action.useFav2() : action.execute();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnNotFoundWithoutStaging_whenFavoriteWasDeleted(boolean ajax) throws Exception {
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            assertThat(useFavorite(ajax, "42")).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(404);
            verify(data.constructed().getFirst()).getFavorite(42);
            verifyNoMoreInteractions(data.constructed().getFirst());
            verify(bean, org.mockito.Mockito.never()).addStashItem(any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnBadRequestWithoutLookup_whenFavoriteIdentifierIsInvalid(boolean ajax) throws Exception {
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            assertThat(useFavorite(ajax, "-1")).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(400);
            data.constructed().forEach(mock -> verifyNoInteractions(mock));
            verify(bean, org.mockito.Mockito.never()).addStashItem(any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad", "-1", "2147483648"})
    void shouldGenerateSafeCardKey_whenRandomIdentifierIsInvalid(String randomId) throws Exception {
        request.setParameter("randomId", randomId);
        when(bean.acceptOrNextStashKey(randomId, RxStashIds.DEFAULT_BOUND)).thenReturn(99L);
        var favorite = mock(RxPrescriptionData.Favorite.class);
        when(favorite.getProviderNo()).thenReturn("999998");
        var prescription = mock(RxPrescriptionData.Prescription.class);
        try (var data = mockConstruction(RxPrescriptionData.class, (mock, context) -> {
            when(mock.getFavorite(42)).thenReturn(favorite);
            when(mock.newPrescription("999998", 1, favorite)).thenReturn(prescription);
        }); var util = mockStatic(RxUtil.class)) {
            util.when(() -> RxUtil.isRxUniqueInStash(bean, prescription)).thenReturn(true);
            assertThat(useFavorite(true, "42")).isEqualTo("useFav2");
            assertThat(response.getStatus()).isEqualTo(200);
            verify(prescription).setRandomId(99L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnServerErrorWithoutSuccessResult_whenFavoriteLookupFails(boolean ajax) throws Exception {
        try (var data = mockConstruction(RxPrescriptionData.class, (mock, context) ->
                when(mock.getFavorite(42)).thenThrow(new IllegalStateException("fixture failure")))) {
            assertThat(useFavorite(ajax, "42")).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(data.constructed()).hasSize(1);
            verify(bean, org.mockito.Mockito.never()).addStashItem(any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveSuccessfulStaging_whenFavoriteExists(boolean ajax) throws Exception {
        var favorite = mock(RxPrescriptionData.Favorite.class);
        when(favorite.getProviderNo()).thenReturn("999998");
        when(bean.acceptOrNextStashKey("77", RxStashIds.DEFAULT_BOUND)).thenReturn(77L);
        var prescription = mock(RxPrescriptionData.Prescription.class);
        when(bean.getProviderNo()).thenReturn("999998");
        when(bean.getDemographicNo()).thenReturn(1);
        when(bean.addStashItem(login, prescription)).thenReturn(3);
        when(bean.getStashItem(3)).thenReturn(prescription);
        try (var data = mockConstruction(RxPrescriptionData.class, (mock, context) -> {
            when(mock.getFavorite(42)).thenReturn(favorite);
            when(mock.newPrescription("999998", 1, favorite)).thenReturn(prescription);
        }); var util = mockStatic(RxUtil.class)) {
            util.when(() -> RxUtil.isRxUniqueInStash(bean, prescription)).thenReturn(true);
            util.when(() -> RxUtil.trimSpecial(prescription)).thenReturn("Synthetic instructions");
            assertThat(useFavorite(ajax, "42")).isEqualTo(ajax ? "useFav2" : "success");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(data.constructed()).hasSize(2);
            verify(bean).addStashItem(login, prescription);
            verify(bean).setStashIndex(3);
            if (ajax) verify(prescription).setRandomId(77L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnNotFoundBeforeEditing_whenFavoriteWasDeleted(boolean ajax) throws Exception {
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            var action = new RxUpdateFavorite2Action();
            action.setFavoriteId("42");
            action.setRepeat("0");
            assertThat(ajax ? action.ajaxEditFavorite() : action.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(404);
            verify(data.constructed().getFirst()).getFavorite(42);
            verifyNoMoreInteractions(data.constructed().getFirst());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnBadRequestBeforeEditing_whenFavoriteIdentifierIsInvalid(boolean ajax) throws Exception {
        request.setParameter("favoriteId", "bad");
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            var action = new RxUpdateFavorite2Action();
            action.setFavoriteId("bad");
            assertThat(ajax ? action.ajaxEditFavorite() : action.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(data.constructed()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnForbiddenWithoutLookup_whenPrescriptionAccessIsDenied(boolean ajax) throws Exception {
        when(security.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(false);
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            assertThatThrownBy(() -> useFavorite(ajax, "42")).isInstanceOf(SecurityException.class);
            assertThat(data.constructed()).isEmpty();
            verify(bean, org.mockito.Mockito.never()).addStashItem(any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnConflictWithoutLookup_whenPrescriptionSessionIsMissing(boolean ajax) throws Exception {
        request.getSession().invalidate();
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            assertThat(useFavorite(ajax, "42")).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(data.constructed()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void shouldRejectUnsupportedMethods_beforeEditingFavorite(String method) throws Exception {
        request.setMethod(method);
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            var action = new RxUpdateFavorite2Action();
            assertThat(action.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            response.setCommitted(false);
            response.reset();
            assertThat(action.ajaxEditFavorite()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(data.constructed()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldOpenFavoriteEditor_whenNoEditWasRequested(String method) throws Exception {
        request.setMethod(method);
        request.removeParameter("favoriteId");
        try (var data = mockConstruction(RxPrescriptionData.class)) {
            assertThat(new io.github.carlos_emr.carlos.prescript.gate.ViewEditFavorites2Action().execute()).isEqualTo("success");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(data.constructed()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectForeignFavorite_beforeApplyingEdits(boolean ajax) throws Exception {
        var favorite = mock(RxPrescriptionData.Favorite.class);
        when(favorite.getProviderNo()).thenReturn("999997");
        when(bean.getProviderNo()).thenReturn("999998");
        try (var data = mockConstruction(RxPrescriptionData.class, (mock, context) ->
                when(mock.getFavorite(42)).thenReturn(favorite))) {
            var action = new RxUpdateFavorite2Action();
            action.setFavoriteId("42");
            action.setRepeat("0");
            assertThat(ajax ? action.ajaxEditFavorite() : action.execute()).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(403);
            verify(favorite).getProviderNo();
            verifyNoMoreInteractions(favorite);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldEditOwnFavorite_whenNoPatientWorkspaceIsOpen(boolean ajax) throws Exception {
        request.getSession().invalidate();
        request.setParameter("takeMin", "1");
        request.setParameter("takeMax", "1");
        var favorite = mock(RxPrescriptionData.Favorite.class);
        when(favorite.getProviderNo()).thenReturn("999998");
        try (var data = mockConstruction(RxPrescriptionData.class, (mock, context) ->
                when(mock.getFavorite(42)).thenReturn(favorite))) {
            var action = new RxUpdateFavorite2Action();
            action.setFavoriteId("42");
            action.setRepeat("0");
            action.setTakeMin("1");
            action.setTakeMax("1");
            assertThat(ajax ? action.ajaxEditFavorite() : action.execute()).isEqualTo(ajax ? "none" : "success");
            assertThat(response.getStatus()).isEqualTo(ajax ? 204 : 200);
            verify(favorite).Save();
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @ValueSource(strings = {"true", "false"})
    void shouldApplyExplicitDispensingFlag_whenSavingOwnFavorite(String flag) throws Exception {
        var favorite = mock(RxPrescriptionData.Favorite.class);
        when(favorite.getProviderNo()).thenReturn("999998");
        when(bean.getProviderNo()).thenReturn("999998");
        request.setParameter("takeMin", "1");
        request.setParameter("takeMax", "1");
        request.setParameter("repeat", "0");
        for (String field : new String[] {"nosubs", "prn", "customInstr"}) request.setParameter(field, "false");
        if (flag != null) request.setParameter("dispenseInternal", flag);
        try (var data = mockConstruction(RxPrescriptionData.class, (mock, context) ->
                when(mock.getFavorite(42)).thenReturn(favorite))) {
            assertThat(new RxUpdateFavorite2Action().ajaxEditFavorite()).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(response.getContentAsByteArray()).isEmpty();
            if (flag == null) verify(favorite, org.mockito.Mockito.never()).setDispenseInternal(any());
            else verify(favorite).setDispenseInternal("true".equals(flag));
            verify(favorite).Save();
        }
    }

    @Test
    void shouldNotPublishNoContent_whenFavoriteSaveFails() {
        var favorite = mock(RxPrescriptionData.Favorite.class);
        when(favorite.getProviderNo()).thenReturn("999998");
        when(bean.getProviderNo()).thenReturn("999998");
        when(favorite.Save()).thenThrow(new IllegalStateException("fixture persistence failure"));
        request.setParameter("takeMin", "1");
        request.setParameter("takeMax", "1");
        request.setParameter("repeat", "0");
        for (String field : new String[] {"nosubs", "prn", "customInstr"}) request.setParameter(field, "false");
        try (var data = mockConstruction(RxPrescriptionData.class, (mock, context) ->
                when(mock.getFavorite(42)).thenReturn(favorite))) {
            assertThatThrownBy(() -> new RxUpdateFavorite2Action().ajaxEditFavorite())
                    .isInstanceOf(IllegalStateException.class);
            assertThat(response.getStatus()).isNotEqualTo(204);
            verify(favorite).Save();
        }
    }

    @Test
    void shouldRejectMissingFavorite_beforeCreatingAnEmptyPrescription() {
        assertThatThrownBy(() -> new RxPrescriptionData().newPrescription("999998", 1,
                (RxPrescriptionData.Favorite) null)).isInstanceOf(IllegalArgumentException.class);
    }
}
