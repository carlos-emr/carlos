/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;

import io.github.carlos_emr.carlos.appt.location.AppointmentLocationChangeRefusedException;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationList;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationService;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationStyle;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;

/**
 * Covers the Locations tab action (#3982): privileges on {@code _admin.schedule} or {@code _admin},
 * POST-only changes answering 405 to GET/HEAD before anything is changed, and refused changes
 * shown as messages.
 */
@DisplayName("AppointmentLocationSettings2Action")
@Tag("unit")
@Tag("appointment")
class AppointmentLocationSettings2ActionUnitTest extends CarlosWebTestBase {

    private AppointmentLocationService locationService;
    private TestAction action;
    private LookupListItem virtual;

    /** Answers message keys, with their parameters, instead of resolving bundles. */
    private static final class TestAction extends AppointmentLocationSettings2Action {
        TestAction(io.github.carlos_emr.carlos.managers.SecurityInfoManager securityInfoManager,
                   AppointmentLocationService locationService) {
            super(securityInfoManager, locationService, mock(PlatformTransactionManager.class));
        }

        @Override
        public String getText(String key) {
            return key;
        }

        @Override
        public String getText(String key, String[] args) {
            return key + Arrays.toString(args);
        }
    }

    @BeforeEach
    void setUpAction() {
        locationService = mock(AppointmentLocationService.class);
        virtual = new LookupListItem();
        virtual.setId(11);
        virtual.setLabel("Virtual");
        virtual.setActive(true);
        when(locationService.load(any())).thenReturn(AppointmentLocationList.of(List.of(virtual), false));
        action = new TestAction(mockSecurityInfoManager, locationService);
    }

    @Test
    void shouldRenderList_withPageAttributes() throws Exception {
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(mockRequest.getAttribute("locations")).isEqualTo(List.of(virtual));
        assertThat(mockRequest.getAttribute("activeLocationCount")).isEqualTo(1);
        assertThat(mockRequest.getAttribute("siteSetup")).isEqualTo(false);
        assertThat(mockRequest.getAttribute("canChange")).isEqualTo(true);
        assertThat(mockRequest.getAttribute("canDeactivate")).isEqualTo(true);
        assertThat(mockRequest.getAttribute("locationIcons")).isEqualTo(AppointmentLocationStyle.ICONS);
    }

    @Test
    void shouldRenderReadOnly_forUserWithReadOnly() throws Exception {
        denyPrivilege("_admin.schedule", "w");
        denyPrivilege("_admin", "w");
        denyPrivilege("_admin.schedule", "d");
        denyPrivilege("_admin", "d");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockRequest.getAttribute("canChange")).isEqualTo(false);
        assertThat(mockRequest.getAttribute("canDeactivate")).isEqualTo(false);
    }

    @Test
    void shouldAllowView_withAdminReadInsteadOfScheduleRead() throws Exception {
        denyPrivilege("_admin.schedule", "r");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldRefuseView_withoutReadOnEitherObject() {
        denyPrivilege("_admin.schedule", "r");
        denyPrivilege("_admin", "r");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.schedule)");
        verifyNoInteractions(locationService);
    }

    @Test
    void shouldRefuseChange_withoutWriteOnEitherObject() {
        denyPrivilege("_admin.schedule", "w");
        denyPrivilege("_admin", "w");
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "add");
        action.setName("Room 5");

        assertThatThrownBy(() -> executeAction(action)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(locationService);
    }

    @Test
    void shouldRefuseDeactivate_withWriteButNoDelete() {
        denyPrivilege("_admin.schedule", "d");
        denyPrivilege("_admin", "d");
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "deactivate");
        action.setId(11);

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.schedule)");
        verifyNoInteractions(locationService);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, add", "GET, addMany", "GET, rename", "GET, updateColour", "GET, updateIcon",
            "GET, moveUp", "GET, moveDown", "GET, deactivate", "GET, restore",
            "HEAD, add", "HEAD, deactivate", "HEAD, rename",
            // The page opens without a dispatch, so one that names no change is refused too.
            "GET, view", "GET, Ajouter"
    })
    void shouldAnswer405WithoutChanging_whenChangeIsNotPosted(String method, String dispatch) throws Exception {
        mockRequest.setMethod(method);
        addRequestParameter("dispatch", dispatch);
        action.setId(11);
        action.setName("Room 5");
        action.setNames("Room 6");
        action.setColour("#123456");
        action.setIcon("fa-house");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(locationService);
    }

    @Test
    void shouldAddLocation_whenPosted() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "add");
        action.setName("Room 5");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(locationService).addLocation(mockLoggedInInfo, "Room 5");
        assertThat(action.getActionMessages()).containsExactly("admin.appt.location.msg.added");
    }

    @Test
    void shouldReportAddedAndSkipped_whenListPasted() throws Exception {
        when(locationService.addLocations(mockLoggedInInfo, "A\nB\nA"))
                .thenReturn(new AppointmentLocationService.AddResult(2, 1));
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "addMany");
        action.setNames("A\nB\nA");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionMessages()).containsExactly("admin.appt.location.msg.addedMany[2, 1]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rename", "updateColour", "updateIcon", "moveUp", "moveDown", "deactivate", "restore"})
    void shouldRouteChange_toService(String dispatch) throws Exception {
        when(locationService.move(any(), anyInt(), anyBoolean(), any())).thenReturn(true);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", dispatch);
        action.setId(11);
        action.setName("Telehealth");
        action.setColour("#123456");
        action.setIcon("fa-video");
        action.setPosition(2);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        switch (dispatch) {
            case "rename" -> verify(locationService).rename(mockLoggedInInfo, 11, "Telehealth");
            case "updateColour" -> verify(locationService).updateColour(mockLoggedInInfo, 11, "#123456");
            case "updateIcon" -> verify(locationService).updateIcon(mockLoggedInInfo, 11, "fa-video");
            case "moveUp" -> verify(locationService).move(mockLoggedInInfo, 11, true, 2);
            case "moveDown" -> verify(locationService).move(mockLoggedInInfo, 11, false, 2);
            case "deactivate" -> verify(locationService).deactivate(mockLoggedInInfo, 11);
            default -> verify(locationService).restore(mockLoggedInInfo, 11);
        }
        assertThat(action.getActionErrors()).isEmpty();
        assertThat(action.getActionMessages()).hasSize(1);
    }

    @Test
    void shouldShowReason_whenServiceRefusesChange() throws Exception {
        when(locationService.addLocation(mockLoggedInInfo, "virtual")).thenThrow(
                new AppointmentLocationChangeRefusedException("admin.appt.location.msg.nameInUse", "virtual"));
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "add");
        action.setName("virtual");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).containsExactly("admin.appt.location.msg.nameInUse[virtual]");
        assertThat(action.getActionMessages()).isEmpty();
        assertThat(mockRequest.getAttribute("locations")).isEqualTo(List.of(virtual));
        // The page offers the refused name again.
        assertThat(mockRequest.getAttribute("refusedDispatch")).isEqualTo("add");
    }

    @Test
    void shouldIssueAddToken_forUserWhoCanChange() throws Exception {
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

        assertThat((String) mockRequest.getAttribute("locationAddToken")).isNotBlank();
        assertThat((String) mockRequest.getAttribute("locationPasteToken")).isNotBlank()
                .isNotEqualTo(mockRequest.getAttribute("locationAddToken"));
        assertThat(mockRequest.getAttribute("defaultLocationIcon")).isEqualTo(AppointmentLocationStyle.DEFAULT_ICON);
    }

    @Test
    void shouldAddOnce_whenSameRenderingIsPostedTwice() throws Exception {
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        String token = (String) mockRequest.getAttribute("locationPasteToken");
        when(locationService.addLocations(mockLoggedInInfo, "A\nB"))
                .thenReturn(new AppointmentLocationService.AddResult(2, 0));
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "addMany");
        addRequestParameter(FormSubmissionGuard.PARAMETER, token);
        action.setNames("A\nB");
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

        TestAction repeat = new TestAction(mockSecurityInfoManager, locationService);
        repeat.setNames("A\nB");
        assertThat(executeAction(repeat)).isEqualTo(ActionSupport.SUCCESS);

        verify(locationService, times(1)).addLocations(mockLoggedInInfo, "A\nB");
        // The repeat ends as the first did, rather than reporting the names as already taken.
        assertThat(repeat.getActionMessages()).containsExactly("admin.appt.location.msg.addedMany[2, 0]");
        assertThat(repeat.getActionErrors()).isEmpty();
    }

    @Test
    void shouldRefusePaste_whenSentWithTheAddFormsToken() throws Exception {
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        String addToken = (String) mockRequest.getAttribute("locationAddToken");
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "addMany");
        addRequestParameter(FormSubmissionGuard.PARAMETER, addToken);
        TestAction paste = new TestAction(mockSecurityInfoManager, locationService);
        paste.setNames("A\nB");

        assertThat(executeAction(paste)).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        verify(locationService, never()).addLocations(any(), any());
    }

    @Test
    void shouldStartAdd_atReadCommitted() throws Exception {
        // The list lock only helps if the reads after it see the change before it (MariaDB).
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        AppointmentLocationSettings2Action plain = new AppointmentLocationSettings2Action(mockSecurityInfoManager,
                locationService, transactionManager) {
            @Override
            public String getText(String key) {
                return key;
            }
        };
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "add");
        plain.setName("Room 5");

        assertThat(executeAction(plain)).isEqualTo(ActionSupport.SUCCESS);
        org.mockito.ArgumentCaptor<org.springframework.transaction.TransactionDefinition> definition =
                org.mockito.ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getIsolationLevel())
                .isEqualTo(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Test
    void shouldRefuseAdd_whenTokenWasNotIssuedBySession() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "add");
        addRequestParameter(FormSubmissionGuard.PARAMETER, "not-a-token-this-session-issued");
        action.setName("Room 5");

        assertThat(executeAction(action)).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        verify(locationService, never()).addLocation(any(), any());
    }

    @Test
    void shouldRefuseChange_withoutLocationId() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "rename");
        action.setName("Telehealth");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).containsExactly("admin.appt.location.msg.notFound");
        verify(locationService, org.mockito.Mockito.never()).rename(any(), anyInt(), any());
    }
}
