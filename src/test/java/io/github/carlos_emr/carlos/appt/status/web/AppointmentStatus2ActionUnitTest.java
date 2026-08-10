/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.status.web;

import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        action = new TestAppointmentStatus2Action(appointmentStatusMgr);
    }

    @Test
    void shouldLoadExistingStatus_forEdit() throws Exception {
        addRequestParameter("dispatch", "modify");
        action.setId(13);

        assertThat(executeAction(action)).isEqualTo("edit");
        assertThat(action.getId()).isEqualTo(13);
        assertThat(action.getApptStatus()).isEqualTo("N");
        assertThat(action.getApptDesc()).isEqualTo("No Show");
        assertThat(action.getApptColor()).isEqualTo("#cccccc");
        assertThat(action.isLegacyColor()).isFalse();
    }

    @Test
    void shouldRequireScheduleAdminReadPrivilege_forViews() {
        denyPrivilege("_admin.schedule", "r");
        allowPrivilege("_appointment", "w");
        addRequestParameter("dispatch", "view");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.schedule");
    }

    @Test
    void shouldRequireScheduleAdminWritePrivilege_forMutations() {
        denyPrivilege("_admin.schedule", "w");
        allowPrivilege("_appointment", "w");
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "reset");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.schedule");
        verify(appointmentStatusMgr, never()).reset();
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
    void shouldUseSafeDefault_whenEditingStatusWithLegacyBlankColour() throws Exception {
        AppointmentStatus legacyStatus = new AppointmentStatus();
        legacyStatus.setId(14);
        legacyStatus.setStatus("C");
        legacyStatus.setDescription("Cancelled");
        legacyStatus.setColor("");
        when(appointmentStatusMgr.getStatus(14)).thenReturn(legacyStatus);
        addRequestParameter("dispatch", "modify");
        action.setId(14);

        assertThat(executeAction(action)).isEqualTo("edit");
        assertThat(action.getApptOldColor()).isEmpty();
        assertThat(action.getApptColor()).isEqualTo("#FFFFFF");
        assertThat(action.isLegacyColor()).isTrue();
    }

    @Test
    void shouldShowValidationError_whenEditIdDoesNotExist() throws Exception {
        addRequestParameter("dispatch", "modify");
        action.setId(9999);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).isNotEmpty();
    }

    @Test
    void shouldPersistValidatedDescriptionAndColour_whenUpdatingStatus() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc("  Missed appointment  ");
        action.setApptColor("#abcdef");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).modifyStatus(13, "Missed appointment", "#abcdef");
        assertThat(action.getActionMessages()).isNotEmpty();
    }

    @Test
    void shouldPersistDescription_whenOnlyDescriptionEdited() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc("Missed appointment");
        action.setApptColor("#cccccc");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).modifyStatus(13, "Missed appointment", "#cccccc");
    }

    @Test
    void shouldPersistColour_whenOnlyColourEdited() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc("No Show");
        action.setApptColor("#123456");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).modifyStatus(13, "No Show", "#123456");
    }

    @Test
    void shouldPreserveLegacyColour_whenOnlyDescriptionEdited() throws Exception {
        AppointmentStatus legacyStatus = new AppointmentStatus();
        legacyStatus.setId(14);
        legacyStatus.setStatus("C");
        legacyStatus.setDescription("Cancelled");
        legacyStatus.setColor("");
        legacyStatus.setEditable(1);
        when(appointmentStatusMgr.getStatus(14)).thenReturn(legacyStatus);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(14);
        action.setApptDesc("Patient cancelled");
        action.setApptColor("#FFFFFF");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).modifyStatus(14, "Patient cancelled", "");
    }

    @Test
    void shouldPersistWhite_whenLegacyColourExplicitlyChanged() throws Exception {
        AppointmentStatus legacyStatus = new AppointmentStatus();
        legacyStatus.setId(14);
        legacyStatus.setStatus("C");
        legacyStatus.setDescription("Cancelled");
        legacyStatus.setColor("");
        legacyStatus.setEditable(1);
        when(appointmentStatusMgr.getStatus(14)).thenReturn(legacyStatus);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(14);
        action.setApptDesc("Patient cancelled");
        action.setApptColor("#FFFFFF");
        action.setReplaceLegacyColor(true);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).modifyStatus(14, "Patient cancelled", "#FFFFFF");
    }

    @Test
    void shouldRejectUpdate_withoutMutationWhenValuesInvalid() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc(" ");
        action.setApptColor("red");

        assertThat(executeAction(action)).isEqualTo("edit");
        assertThat(action.getActionErrors()).hasSize(2);
        verify(appointmentStatusMgr, never()).modifyStatus(anyInt(), anyString(), anyString());
    }

    @Test
    void shouldRejectUpdate_withoutMutationWhenDescriptionOverlength() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc("d".repeat(31));
        action.setApptColor("#abcdef");

        assertThat(executeAction(action)).isEqualTo("edit");
        assertThat(action.getActionErrors())
                .containsExactly("admin.appt.status.mgr.error.descriptionLength");
        verify(appointmentStatusMgr, never()).modifyStatus(anyInt(), anyString(), anyString());
    }

    @Test
    void shouldRejectUpdate_withoutMutationWhenIdMissing() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setApptDesc("Valid description");
        action.setApptColor("#abcdef");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).isNotEmpty();
        assertThat(mockRequest.getAttribute("allStatus")).isEqualTo(List.of());
        verify(appointmentStatusMgr, never()).modifyStatus(anyInt(), anyString(), anyString());
    }

    @Test
    void shouldChangeStatus_whenPostValuesValid() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "changestatus");
        action.setId(13);
        action.setActive(0);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).changeStatus(13, 0);
    }

    @Test
    void shouldRejectChange_whenActiveValueInvalid() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "changestatus");
        action.setId(13);
        action.setActive(2);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).isNotEmpty();
        verify(appointmentStatusMgr, never()).changeStatus(13, 2);
    }

    @Test
    void shouldRejectChange_whenStatusIsNotEditable() throws Exception {
        AppointmentStatus protectedStatus = new AppointmentStatus();
        protectedStatus.setId(14);
        protectedStatus.setEditable(0);
        when(appointmentStatusMgr.getStatus(14)).thenReturn(protectedStatus);
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "changestatus");
        action.setId(14);
        action.setActive(0);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors())
                .containsExactly("admin.appt.status.mgr.error.notEditable");
        verify(appointmentStatusMgr, never()).changeStatus(14, 0);
    }

    @Test
    void shouldResetStatuses_whenRequestIsPost() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "reset");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).reset();
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "changestatus", "reset"})
    void shouldRejectMutationDispatches_whenRequestIsGet(String dispatch) throws Exception {
        mockRequest.setMethod("GET");
        addRequestParameter("dispatch", dispatch);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(appointmentStatusMgr, never()).reset();
        verify(appointmentStatusMgr, never()).changeStatus(anyInt(), anyInt());
        verify(appointmentStatusMgr, never()).modifyStatus(anyInt(), anyString(), anyString());
    }

    private static final class TestAppointmentStatus2Action extends AppointmentStatus2Action {
        private final AppointmentStatusMgr appointmentStatusMgr;

        private TestAppointmentStatus2Action(AppointmentStatusMgr appointmentStatusMgr) {
            this.appointmentStatusMgr = appointmentStatusMgr;
        }

        @Override
        public AppointmentStatusMgr getApptStatusMgr() {
            return appointmentStatusMgr;
        }

        @Override
        public String getText(String key) {
            return key;
        }
    }
}
