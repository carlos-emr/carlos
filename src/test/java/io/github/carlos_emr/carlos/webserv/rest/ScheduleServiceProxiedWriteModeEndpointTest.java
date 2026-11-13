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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.GenericApplicationContext;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.AppointmentUpdateException;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.SchedulingResponse;
import io.github.carlos_emr.carlos.webserv.rest.util.WebServiceLoggingAdvice;

/**
 * The {@code X-Carlos-Appointment-Write-Mode} contract of {@code docs/appointment-api-write-modes.md},
 * served by {@link ScheduleService} the way production publishes it: a singleton behind the CGLIB
 * proxy Spring builds for {@link WebServiceLoggingAdvice}.
 *
 * <p>{@link ScheduleServiceEndpointTest} publishes a plain {@code new ScheduleService()}. CXF
 * injects a {@code @Context} field into the object it is given, which for a proxied resource is the
 * proxy and not the target the resource method runs on. A resource that read the header through
 * such a field passed there and ignored the header on every deployed request; this suite fails for
 * it.</p>
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ScheduleService appointment write modes through the production proxy")
class ScheduleServiceProxiedWriteModeEndpointTest extends CarlosRestTestBase {

    private static final String HEADER = ScheduleService.APPOINTMENT_WRITE_MODE_HEADER;

    @Mock
    private ScheduleManager scheduleManager;
    @Mock
    private AppointmentManager appointmentManager;
    @Mock
    private SecurityInfoManager securityInfoManager;

    private GenericApplicationContext context;

    @Override
    protected Object getServiceBean() {
        ScheduleService service = new ScheduleService();
        injectDependency(service, "scheduleManager", scheduleManager);
        injectDependency(service, "appointmentManager", appointmentManager);
        injectDependency(service, "securityInfoManager", securityInfoManager);

        context = new GenericApplicationContext();
        // What <aop:aspectj-autoproxy proxy-target-class="true"/> registers in applicationContext.xml.
        AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(context);
        AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(context);
        context.registerBean(WebServiceLoggingAdvice.class);
        context.registerBean("scheduleService", ScheduleService.class, () -> service);
        context.refresh();

        Object published = context.getBean("scheduleService");
        assertThat(AopUtils.isCglibProxy(published)).as("published as a CGLIB proxy, as in production").isTrue();
        assertThat(published).isNotSameAs(service);
        return published;
    }

    @BeforeEach
    void allowAppointmentWrites() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any())).thenReturn(true);
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    /** The full update and the status, type and urgency routes. */
    static Stream<String> updateRoutes() {
        return Stream.of("/schedule/updateAppointment", "/schedule/appointment/42/updateStatus",
                "/schedule/appointment/42/updateType", "/schedule/appointment/42/updateUrgency");
    }

    private static Appointment updatedAppointment() {
        Appointment updated = new Appointment();
        updated.setId(42);
        updated.setUpdateDateTime(new Date(2000));
        return updated;
    }

    private Response post(String route, String editVersionField, String... modes) {
        WebClient request = request().path(route);
        if (modes.length > 0) {
            request.header(HEADER, (Object[]) modes);
        }
        return request.post(Entity.json("{\"id\":42," + editVersionField + "\"status\":\"new\"}"));
    }

    /**
     * One array entry is one header line. A line repeating the same value cannot be sent from here:
     * the CXF test client drops a value it already holds. {@link ScheduleServiceUnitTest} covers the
     * list the resource is handed in that case.
     */
    static Stream<Arguments> invalidModeSelections() {
        return updateRoutes().flatMap(route -> Stream.of(
                Arguments.of(route, new String[] {"bogus"}),
                Arguments.of(route, new String[] {""}),
                Arguments.of(route, new String[] {"GUARDED"}),
                Arguments.of(route, new String[] {"guarded,legacy-echo"}),
                Arguments.of(route, new String[] {"guarded", "legacy-echo"})));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("invalidModeSelections")
    @DisplayName("an unknown, blank, combined or repeated mode is HTTP 400 before the update manager is called")
    void shouldRejectInvalidMode_beforeCallingTheUpdateManager(String route, String[] modes) {
        try (Response response = post(route, "\"editVersion\":\"1000\",", modes)) {
            assertThat(response.getStatus()).isEqualTo(400);
        }
        verifyNoInteractions(scheduleManager);
    }

    static Stream<Arguments> missingVersions() {
        return updateRoutes().flatMap(route -> Stream.of("", "\"editVersion\":null,", "\"editVersion\":\"\",",
                "\"editVersion\":\"   \",").map(version -> Arguments.of(route, version)));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("missingVersions")
    @DisplayName("guarded without an editVersion is HTTP 400 before the update manager is called")
    void shouldRequireEditVersion_whenModeIsGuarded(String route, String editVersionField) {
        try (Response response = post(route, editVersionField, "guarded")) {
            assertThat(response.getStatus()).isEqualTo(400);
        }
        verifyNoInteractions(scheduleManager);
    }

    static Stream<Arguments> guardedRefusals() {
        return updateRoutes().flatMap(route -> Stream.of(
                Arguments.of(route, AppointmentUpdateException.Reason.STALE_VERSION, 409, "APPOINTMENT_STALE_VERSION"),
                Arguments.of(route, AppointmentUpdateException.Reason.NOT_FOUND, 404, "APPOINTMENT_NOT_FOUND")));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("guardedRefusals")
    @DisplayName("guarded with an editVersion is checked by the manager: 409 when stale, 404 when missing")
    void shouldReportManagerRefusal_whenModeIsGuarded(String route, AppointmentUpdateException.Reason reason,
            int status, String code) {
        when(scheduleManager.updateAppointment(any(), eq(42), eq("1000"), any()))
                .thenThrow(new AppointmentUpdateException(reason));

        try (Response response = post(route, "\"editVersion\":\"1000\",", "guarded")) {
            assertThat(response.getStatus()).isEqualTo(status);
            assertThat(response.readEntity(Map.class).get("code")).isEqualTo(code);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("updateRoutes")
    @DisplayName("guarded with a current editVersion updates and returns the next editVersion")
    void shouldUpdateWithVersion_whenModeIsGuarded(String route) {
        when(scheduleManager.updateAppointment(any(), eq(42), eq("1000"), any())).thenReturn(updatedAppointment());

        try (Response response = post(route, "\"editVersion\":\"1000\",", "guarded")) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(SchedulingResponse.class).getAppointment().getEditVersion())
                    .isEqualTo("2000");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("updateRoutes")
    @DisplayName("legacy-echo ignores an echoed editVersion and still returns the next one")
    void shouldIgnoreEchoedVersion_whenModeIsLegacyEcho(String route) {
        when(scheduleManager.updateAppointment(any(), eq(42), isNull(), any())).thenReturn(updatedAppointment());

        try (Response response = post(route, "\"editVersion\":\"stale-unaware-echo\",", "legacy-echo")) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(SchedulingResponse.class).getAppointment().getEditVersion())
                    .isEqualTo("2000");
        }
        verify(scheduleManager).updateAppointment(any(), eq(42), isNull(), any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("updateRoutes")
    @DisplayName("without the header a supplied editVersion still reaches the manager")
    void shouldKeepTokenAwareBehaviour_whenHeaderIsAbsent(String route) {
        when(scheduleManager.updateAppointment(any(), eq(42), eq("1000"), any())).thenReturn(updatedAppointment());

        try (Response response = post(route, "\"editVersion\":\"1000\",")) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(scheduleManager).updateAppointment(any(), eq(42), eq("1000"), any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("updateRoutes")
    @DisplayName("without the header and without an editVersion the edit is unconditional")
    void shouldKeepLastWriterWins_whenHeaderAndVersionAreAbsent(String route) {
        when(scheduleManager.updateAppointment(any(), eq(42), isNull(), any())).thenReturn(updatedAppointment());

        try (Response response = post(route, "")) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(scheduleManager).updateAppointment(any(), eq(42), isNull(), any());
    }
}
