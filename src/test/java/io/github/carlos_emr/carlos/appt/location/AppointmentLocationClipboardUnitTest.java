/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentCutRecord2Action;
import io.github.carlos_emr.carlos.appt.ApptData;
import io.github.carlos_emr.carlos.appt.ApptUtil;
import io.github.carlos_emr.carlos.appt.AppointmentClipboardValidationException;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.ErrorPageMessage;

/** Exercises the clipboard boundary used by both Copy and Cut, including list changes before Paste. */
@Tag("unit")
@Tag("appointment")
class AppointmentLocationClipboardUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private OscarAppointmentDao appointmentDao;
    private AppointmentArchiveDao archiveDao;
    private Appointment saved;
    private LookupListItem item;
    private AppointmentLocationResolver resolver;

    @BeforeEach
    void setUpClipboard() {
        request = new MockHttpServletRequest("POST", "/appointment/appointmentcopyrecord");
        response = new MockHttpServletResponse();
        request.setParameter("appointment_no", "42");
        request.setParameter("locationCode", "11");
        request.setParameter("location", "");
        request.setParameter("appointment_date", "2026-11-05");
        request.setParameter("start_time", "09:00");
        request.setParameter("end_time", "09:14");
        request.setParameter("duration", "15");
        request.setParameter("status", "t");
        request.setParameter("demographic_no", "123");
        request.setParameter("keyword", "Test appointment");
        request.setParameter("chart_no", "AB-12345");
        request.setParameter("reason", "Test reason");
        request.setParameter("notes", "Test notes");
        request.setParameter("resources", "Test resources");
        request.setParameter("type", "");
        request.setParameter("remarks", "");

        saved = new Appointment();
        saved.setId(42);
        saved.setLocationCode(11);
        saved.setLocation("Booked room");
        appointmentDao = mock(OscarAppointmentDao.class);
        when(appointmentDao.find(42)).thenReturn(saved);
        // Cut parses a primitive int; clipboard validation holds an Integer and therefore
        // selects find(Object). The real DAO delegates both overloads to EntityManager.find.
        when(appointmentDao.find((Object) Integer.valueOf(42))).thenReturn(saved);
        registerMock(OscarAppointmentDao.class, appointmentDao);
        archiveDao = mock(AppointmentArchiveDao.class);
        registerMock(AppointmentArchiveDao.class, archiveDao);

        item = AppointmentLocationTestItems.item(11, "Booked room", 1, true);
        LookupList list = new LookupList();
        list.setItems(new ArrayList<>(List.of(item)));
        LookupListDao lookupListDao = mock(LookupListDao.class);
        when(lookupListDao.findByName(AppointmentLocationResolver.LIST_NAME)).thenReturn(list);
        resolver = new AppointmentLocationResolver(lookupListDao, () -> false);
        registerMock(AppointmentLocationResolver.class, resolver);

        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", mock(LoggedInInfo.class));
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
    }

    @AfterEach
    void clearActionContext() {
        ActionContext.clear();
    }

    @Test
    void shouldKeepCopiedLocation_whenLastItemDisabledBeforePaste() {
        ApptUtil.copyAppointmentIntoSession(request);
        item.setActive(false);

        ApptData clipboard = ApptUtil.getAppointmentFromSession(request);
        assertThat(resolver.load().isLocationMode()).isFalse();
        assertThat(clipboard.getLocation()).isEqualTo("Booked room");
        assertThat(clipboard.getLocationCode()).isEqualTo("11");
        Appointment pasted = new Appointment();
        resolver.applyPostedLocation(pasted, null, clipboard.getLocation());
        assertThat(pasted.getLocation()).isEqualTo("Booked room");
        assertThat(saved.getLocation()).isEqualTo("Booked room");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldKeepHistoricalLabel_whenItemRenamedBeforePaste(boolean active) {
        item.setLabel("Renamed room");
        item.setActive(active);
        ApptUtil.copyAppointmentIntoSession(request);

        ApptData clipboard = ApptUtil.getAppointmentFromSession(request);
        AppointmentLocationList.Choice choice = resolver.load().pastedChoice(clipboard.getLocationCode(), clipboard.getLocation());
        assertThat(choice).isEqualTo(new AppointmentLocationList.Choice("legacy", "Booked room"));
        assertThat(saved.getLocation()).isEqualTo("Booked room");
        assertThat(saved.getLocationCode()).isEqualTo(11);
    }

    @Test
    void shouldCopyChangedChoice_withoutChangingSourceAppointment() {
        saved.setLocationCode(12);
        saved.setLocation("Another room");

        ApptUtil.copyAppointmentIntoSession(request);

        ApptData clipboard = ApptUtil.getAppointmentFromSession(request);
        assertThat(clipboard.getLocation()).isEqualTo("Booked room");
        assertThat(clipboard.getLocationCode()).isEqualTo("11");
        assertThat(saved.getLocation()).isEqualTo("Another room");
        assertThat(saved.getLocationCode()).isEqualTo(12);
        verify(appointmentDao).find((Object) Integer.valueOf(42));
    }

    @Test
    void shouldCutWithLabelSnapshot_whenSourceLocationIsUnchanged() throws Exception {
        assertThat(new AppointmentCutRecord2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(ApptUtil.getAppointmentFromSession(request).getLocation()).isEqualTo("Booked room");
        verify(archiveDao).archiveAppointment(saved);
        verify(appointmentDao).remove(42);
    }

    @Test
    void shouldKeepChartTextAndUnicode_whenCuttingValidAppointment() throws Exception {
        String supplementary = new String(Character.toChars(0x1F600));
        String name = supplementary.repeat(255);
        String reason = supplementary.repeat(79) + "\r\n";
        request.setParameter("chart_no", "AB-12345");
        request.setParameter("keyword", name);
        request.setParameter("reason", reason);
        request.setParameter("notes", supplementary.repeat(255));
        request.setParameter("appointment_date", "2026-11-05");
        request.setParameter("start_time", "9:05");
        request.setParameter("end_time", "10:05:30");
        request.setParameter("reasonCode", "-1");

        assertThat(new AppointmentCutRecord2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        ApptData clipboard = ApptUtil.getAppointmentFromSession(request);
        assertThat(clipboard.getChart_no()).isEqualTo("AB-12345");
        assertThat(clipboard.getName()).isEqualTo(name);
        assertThat(clipboard.getReason()).isEqualTo(reason);
        assertThat(clipboard.getNotes()).isEqualTo(supplementary.repeat(255));
        assertThat(clipboard.getAppointment_date()).isEqualTo("2026-11-05");
        assertThat(clipboard.getStart_time()).isEqualTo("9:05");
        assertThat(clipboard.getEnd_time()).isEqualTo("10:05:30");
        assertThat(clipboard.getReasonCode()).isEqualTo("-1");
        verify(archiveDao).archiveAppointment(saved);
        verify(appointmentDao).remove(42);
    }

    @ParameterizedTest
    @MethodSource("invalidClipboardFields")
    void shouldKeepSourceAndClipboard_whenCutFieldsInvalid(String field, String value) throws Exception {
        ApptData priorClipboard = new ApptData();
        request.getSession().setAttribute("apptBean", priorClipboard);
        request.setParameter(field, value);

        assertThat(new AppointmentCutRecord2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(request.getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo(AppointmentClipboardValidationException.MESSAGE_KEY);
        assertThat(ApptUtil.getAppointmentFromSession(request)).isSameAs(priorClipboard);
        verify(appointmentDao, never()).remove(anyInt());
        verifyNoInteractions(archiveDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"appointment_date", "start_time", "end_time", "duration", "status", "demographic_no",
            "keyword", "chart_no", "reason", "notes", "location", "resources", "type", "remarks"})
    void shouldKeepSourceAndClipboard_whenCoreFieldMissing(String field) throws Exception {
        ApptData priorClipboard = new ApptData();
        request.getSession().setAttribute("apptBean", priorClipboard);
        request.removeParameter(field);

        assertThat(new AppointmentCutRecord2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(request.getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo(AppointmentClipboardValidationException.MESSAGE_KEY);
        assertThat(ApptUtil.getAppointmentFromSession(request)).isSameAs(priorClipboard);
        verify(appointmentDao, never()).remove(anyInt());
        verifyNoInteractions(archiveDao);
    }

    @Test
    void shouldKeepEmptyText_whenCuttingNonPatientBooking() throws Exception {
        for (String field : List.of("demographic_no", "keyword", "chart_no", "reason", "notes", "resources", "type", "remarks")) {
            request.setParameter(field, "");
        }

        assertThat(new AppointmentCutRecord2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        ApptData clipboard = ApptUtil.getAppointmentFromSession(request);
        assertThat(clipboard.getDemographic_no()).isEmpty();
        assertThat(clipboard.getName()).isEmpty();
        assertThat(clipboard.getNotes()).isEmpty();
        assertThat(clipboard.getReason()).isEmpty();
        verify(appointmentDao).remove(42);
    }

    static Stream<Arguments> invalidClipboardFields() {
        return Stream.of(
                Arguments.of("demographic_no", "abc"),
                Arguments.of("demographic_no", "-1"),
                Arguments.of("demographic_no", "+1"),
                Arguments.of("demographic_no", "2147483648"),
                Arguments.of("chart_no", "A\nB"),
                Arguments.of("status", "bad!"),
                Arguments.of("status", ""),
                Arguments.of("duration", "10000"),
                Arguments.of("duration", ""),
                Arguments.of("appointment_date", ""),
                Arguments.of("start_time", ""),
                Arguments.of("end_time", ""),
                Arguments.of("appointment_date", "bad-date"),
                Arguments.of("appointment_date", "2026-99-99"),
                Arguments.of("appointment_date", "2026-02-29"),
                Arguments.of("start_time", "bad-time"),
                Arguments.of("start_time", "99:99"),
                Arguments.of("end_time", "09:05:99"),
                Arguments.of("reasonCode", "bad-code!"),
                Arguments.of("reasonCode", "abc"),
                Arguments.of("reasonCode", "2147483648"),
                Arguments.of("keyword", "x".repeat(256)),
                Arguments.of("reason", "x".repeat(81)),
                Arguments.of("notes", "x".repeat(256)),
                Arguments.of("resources", "x".repeat(256)),
                Arguments.of("keyword", String.valueOf(Character.MIN_HIGH_SURROGATE)),
                Arguments.of("notes", "A" + (char) 0 + "B"));
    }

    @Test
    void shouldKeepSourceAndClipboard_whenCutSelectsUnavailableLocation() throws Exception {
        ApptData priorClipboard = new ApptData();
        request.getSession().setAttribute("apptBean", priorClipboard);
        request.setParameter("locationCode", "99");

        assertThat(new AppointmentCutRecord2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(request.getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo(AppointmentLocationUnavailableException.MESSAGE_KEY);
        assertThat(ApptUtil.getAppointmentFromSession(request)).isSameAs(priorClipboard);
        verify(appointmentDao, never()).remove(anyInt());
        verifyNoInteractions(archiveDao);
        assertThat(saved.getLocation()).isEqualTo("Booked room");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad", "0", "-1", "9999999999999", "99"})
    void shouldRefuseCopy_withoutValidSourceAppointment(String sourceId) {
        if (sourceId == null) {
            request.removeParameter("appointment_no");
        } else {
            request.setParameter("appointment_no", sourceId);
        }
        assertThatThrownBy(() -> ApptUtil.copyAppointmentIntoSession(request)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ApptUtil.getAppointmentFromSession(request)).isNull();
        verifyNoInteractions(archiveDao);
    }
}
