/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.status.web;

import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr;
import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusStyleException;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Statuses tab action: inline description/colour/icon edits (#3982), the editable flag
 * gating only enable/disable (#3888), POST-only changes and the audit trail.
 */
@DisplayName("AppointmentStatus2Action")
@Tag("unit")
@Tag("appointment")
class AppointmentStatus2ActionUnitTest extends CarlosWebTestBase {

    private AppointmentStatusMgr appointmentStatusMgr;
    private TestAppointmentStatus2Action action;

    @BeforeEach
    void setUpAction() {
        appointmentStatusMgr = mock(AppointmentStatusMgr.class);
        when(appointmentStatusMgr.getAllStatus()).thenReturn(List.of());

        AppointmentStatus existingStatus = new AppointmentStatus();
        existingStatus.setId(13);
        existingStatus.setStatus("N");
        existingStatus.setDescription("No Show");
        existingStatus.setColor("#cccccc");
        existingStatus.setEditable(1);
        when(appointmentStatusMgr.getStatus(13)).thenReturn(existingStatus);

        AppointmentStatus coreStatus = new AppointmentStatus();
        coreStatus.setId(14);
        coreStatus.setStatus("C");
        coreStatus.setDescription("Cancelled");
        coreStatus.setColor("#999999");
        coreStatus.setEditable(0);
        when(appointmentStatusMgr.getStatus(14)).thenReturn(coreStatus);

        action = new TestAppointmentStatus2Action(appointmentStatusMgr);
    }

    @Test
    void shouldRenderList_whenRetiredModifyDispatchRequested() throws Exception {
        addRequestParameter("dispatch", "modify");
        action.setId(13);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockRequest.getAttribute("allStatus")).isEqualTo(List.of());
        assertThat(mockRequest.getAttribute("iconSet")).isEqualTo(AppointmentStatusMgr.ICON_SET);
        assertThat(mockRequest.getAttribute("descriptionMaxLength")).isEqualTo(AppointmentStatusMgr.DESCRIPTION_MAX_LENGTH);
    }

    @Test
    void shouldSayChangeWasNotSaved_whenRetiredEditPagePosts() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc("Missed");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).hasSize(1);
        verify(appointmentStatusMgr, never()).updateDescription(anyInt(), any());
        verify(appointmentStatusMgr, never()).updateColour(anyInt(), any());
        verify(appointmentStatusMgr, never()).updateIcon(anyInt(), any());
        verify(appointmentStatusMgr, never()).changeStatus(anyInt(), anyInt());
        verify(appointmentStatusMgr, never()).reset();
        assertThat(mockRequest.getAttribute("allStatus")).isEqualTo(List.of());
    }

    @Test
    void shouldRenderControlsDisabled_forUserWhoCanOnlyRead() throws Exception {
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockRequest.getAttribute("canChange")).isEqualTo(true);

        denyPrivilege("_admin.schedule", "w");
        denyPrivilege("_admin", "w");
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockRequest.getAttribute("canChange")).isEqualTo(false);
    }

    @Test
    void shouldRequireScheduleAdminReadPrivilege_forViews() {
        denyPrivilege("_admin.schedule", "r");
        denyPrivilege("_admin", "r");
        allowPrivilege("_appointment", "w");
        addRequestParameter("dispatch", "view");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.schedule)");
    }

    @Test
    void shouldAllowView_withAdminReadInsteadOfScheduleRead() throws Exception {
        denyPrivilege("_admin.schedule", "r");
        addRequestParameter("dispatch", "view");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reset", "changestatus", "updateDescription", "updateColour", "updateIcon"})
    void shouldRequireScheduleAdminWritePrivilege_forMutations(String dispatch) {
        denyPrivilege("_admin.schedule", "w");
        denyPrivilege("_admin", "w");
        allowPrivilege("_appointment", "w");
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", dispatch);
        action.setId(13);
        action.setApptDesc("Missed");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.schedule)");
        verify(appointmentStatusMgr, never()).reset();
        verify(appointmentStatusMgr, never()).updateDescription(anyInt(), any());
        assertThat(action.audited).isEmpty();
    }

    @Test
    void shouldReportUsedInactiveStatus_fromReturnedListIndex() throws Exception {
        AppointmentStatus firstStatus = new AppointmentStatus();
        firstStatus.setId(901);
        firstStatus.setStatus("H");
        AppointmentStatus usedInactiveStatus = new AppointmentStatus();
        usedInactiveStatus.setId(477);
        usedInactiveStatus.setStatus("c");
        List<AppointmentStatus> reorderedStatuses = List.of(firstStatus, usedInactiveStatus);
        when(appointmentStatusMgr.getAllStatus()).thenReturn(reorderedStatuses);
        when(appointmentStatusMgr.checkStatusUsuage(reorderedStatuses)).thenReturn(1);
        addRequestParameter("dispatch", "view");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockRequest.getAttribute("useStatus")).isEqualTo("c");
        verify(appointmentStatusMgr, never()).getStatus(2);
    }

    @Test
    void shouldReportUsedInactiveStatus_whenItIsFirstListEntry() throws Exception {
        AppointmentStatus usedInactiveStatus = new AppointmentStatus();
        usedInactiveStatus.setId(477);
        usedInactiveStatus.setStatus("c");
        List<AppointmentStatus> statuses = List.of(usedInactiveStatus);
        when(appointmentStatusMgr.getAllStatus()).thenReturn(statuses);
        when(appointmentStatusMgr.checkStatusUsuage(statuses)).thenReturn(0);
        addRequestParameter("dispatch", "view");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockRequest.getAttribute("useStatus")).isEqualTo("c");
    }

    @Test
    void shouldSaveDescriptionAndAudit_whenUpdateDescriptionPosted() throws Exception {
        when(appointmentStatusMgr.updateDescription(13, "Missed appointment")).thenReturn(true);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "updateDescription");
        action.setId(13);
        action.setApptDesc("Missed appointment");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).updateDescription(13, "Missed appointment");
        assertThat(action.getActionErrors()).isEmpty();
        assertThat(action.getActionMessages()).containsExactly("admin.appt.status.mgr.message.updated");
        assertThat(action.audited).containsExactly("updateDescription|appointment_status id=13 description updated");
    }

    @Test
    void shouldSaveColour_whenUpdateColourPosted() throws Exception {
        when(appointmentStatusMgr.updateColour(13, "#abcdef")).thenReturn(true);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "updateColour");
        action.setId(13);
        action.setApptColor("#abcdef");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).updateColour(13, "#abcdef");
        verify(appointmentStatusMgr, never()).updateDescription(anyInt(), any());
        assertThat(action.audited).containsExactly("updateColour|appointment_status id=13 colour updated");
    }

    @Test
    void shouldSaveIcon_whenUpdateIconPosted() throws Exception {
        when(appointmentStatusMgr.updateIcon(13, "noshow.gif")).thenReturn(true);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "updateIcon");
        action.setId(13);
        action.setApptIcon("noshow.gif");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).updateIcon(13, "noshow.gif");
        assertThat(action.audited).containsExactly("updateIcon|appointment_status id=13 icon updated");
    }

    @Test
    void shouldRestyleCoreStatus_whenEditableFlagIsZero() throws Exception {
        // #3888: editable=0 gates only enable/disable; clinics recolour and rename core statuses.
        when(appointmentStatusMgr.updateColour(14, "#123456")).thenReturn(true);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "updateColour");
        action.setId(14);
        action.setApptColor("#123456");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).updateColour(14, "#123456");
        assertThat(action.getActionErrors()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "updateDescription, admin.appt.status.mgr.error.descriptionRequired",
            "updateColour, admin.appt.status.mgr.error.color",
            "updateIcon, admin.appt.status.mgr.error.icon"
    })
    void shouldShowRefusalWithoutAudit_whenManagerRejectsValue(String dispatch, String messageKey) throws Exception {
        AppointmentStatusStyleException refused = new AppointmentStatusStyleException(messageKey);
        when(appointmentStatusMgr.updateDescription(anyInt(), any())).thenThrow(refused);
        when(appointmentStatusMgr.updateColour(anyInt(), any())).thenThrow(refused);
        when(appointmentStatusMgr.updateIcon(anyInt(), any())).thenThrow(refused);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", dispatch);
        action.setId(13);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).containsExactly(messageKey);
        assertThat(action.getActionMessages()).isEmpty();
        assertThat(action.audited).isEmpty();
    }

    @Test
    void shouldRejectStyleEdit_withoutCallingManagerWhenIdMissing() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "updateDescription");
        action.setApptDesc("Anything");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).containsExactly("admin.appt.status.mgr.error.invalidId");
        verify(appointmentStatusMgr, never()).updateDescription(anyInt(), any());
    }

    @Test
    void shouldReportNotFound_whenStatusDisappearsBeforeSave() throws Exception {
        when(appointmentStatusMgr.updateDescription(13, "Gone")).thenReturn(false);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "updateDescription");
        action.setId(13);
        action.setApptDesc("Gone");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).containsExactly("admin.appt.status.mgr.error.notFound");
        assertThat(action.audited).isEmpty();
    }

    @Test
    void shouldChangeStatusAndAudit_whenPostValuesValid() throws Exception {
        when(appointmentStatusMgr.changeStatus(13, 0)).thenReturn(true);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "changestatus");
        action.setId(13);
        action.setActive(0);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).changeStatus(13, 0);
        assertThat(action.audited).containsExactly("changestatus|appointment_status id=13 active=0");
    }

    @Test
    void shouldRejectChange_whenActiveValueInvalid() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "changestatus");
        action.setId(13);
        action.setActive(2);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).containsExactly("admin.appt.status.mgr.error.invalidActive");
        verify(appointmentStatusMgr, never()).changeStatus(anyInt(), anyInt());
    }

    @Test
    void shouldRejectChange_whenStatusIsNotEditable() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "changestatus");
        action.setId(14);
        action.setActive(0);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).containsExactly("admin.appt.status.mgr.error.notEditable");
        verify(appointmentStatusMgr, never()).changeStatus(anyInt(), anyInt());
        assertThat(action.audited).isEmpty();
    }

    @Test
    void shouldResetStatusesAndAudit_whenRequestIsPost() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "reset");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).reset();
        assertThat(action.audited).hasSize(1).first().asString().startsWith("reset|");
    }

    @ParameterizedTest
    @CsvSource({
            "GET, changestatus",
            "GET, reset",
            "GET, updateDescription",
            "GET, updateColour",
            "GET, updateIcon",
            "HEAD, changestatus",
            "HEAD, reset",
            "HEAD, updateDescription",
            "HEAD, updateColour",
            "HEAD, updateIcon"
    })
    void shouldRejectMutationDispatches_whenRequestIsNotPost(String requestMethod, String dispatch)
            throws Exception {
        mockRequest.setMethod(requestMethod);
        addRequestParameter("dispatch", dispatch);
        action.setId(13);
        action.setActive(0);
        action.setApptDesc("Missed");
        action.setApptColor("#abcdef");
        action.setApptIcon("noshow.gif");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(appointmentStatusMgr, never()).reset();
        verify(appointmentStatusMgr, never()).changeStatus(anyInt(), anyInt());
        verify(appointmentStatusMgr, never()).updateDescription(anyInt(), anyString());
        verify(appointmentStatusMgr, never()).updateColour(anyInt(), anyString());
        verify(appointmentStatusMgr, never()).updateIcon(anyInt(), anyString());
        assertThat(action.audited).isEmpty();
    }

    private static final class TestAppointmentStatus2Action extends AppointmentStatus2Action {
        private final AppointmentStatusMgr appointmentStatusMgr;
        private final List<String> audited = new ArrayList<>();

        private TestAppointmentStatus2Action(AppointmentStatusMgr appointmentStatusMgr) {
            this.appointmentStatusMgr = appointmentStatusMgr;
        }

        @Override
        public AppointmentStatusMgr getApptStatusMgr() {
            return appointmentStatusMgr;
        }

        @Override
        protected void audit(String dispatch, String data) {
            audited.add(dispatch + "|" + data);
        }

        @Override
        public String getText(String key) {
            return key;
        }
    }
}
