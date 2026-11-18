/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.ScheduleService;
import io.github.carlos_emr.carlos.webserv.rest.conversion.AppointmentConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;
import jakarta.ws.rs.WebApplicationException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises each actual REST field-edit method through the real locked manager. */
@Tag("unit")
class AppointmentFieldAuditUnitTest extends ScheduleUnitTestBase {
    private OscarAppointmentDao appointments;
    private AppointmentArchiveDao archives;
    private ScheduleManagerImpl manager;
    private ScheduleService service;
    private Appointment current;

    @BeforeEach
    void prepare() {
        appointments=mock(OscarAppointmentDao.class);archives=mock(AppointmentArchiveDao.class);
        when(mockSecurityInfoManager.hasPrivilege(any(),anyString(),anyString(),any())).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(mockLoggedInInfo,TEST_DEMO_NO)).thenReturn(true);
        current=new Appointment();current.setId(67);current.setDemographicNo(TEST_DEMO_NO);current.setProviderNo(TEST_PROVIDER);current.setStatus("t");current.setType("original-type");current.setUrgency("original-urgency");current.setName("FAKE-original-name");
        when(appointments.findForUpdate(67)).thenReturn(current);
        manager=new ScheduleManagerImpl();injectDependency(manager,"oscarAppointmentDao",appointments);injectDependency(manager,"appointmentArchiveDao",archives);injectDependency(manager,"securityInfoManager",mockSecurityInfoManager);
        service=new ScheduleService(){
            @Override protected LoggedInInfo getLoggedInInfo(){return mockLoggedInInfo;}
            @Override protected List<String> getRequestHeader(String name){return null;}
        };
        injectDependency(service,"scheduleManager",manager);injectDependency(service,"securityInfoManager",mockSecurityInfoManager);
    }
    private void edit(String field,AppointmentTo1 input) throws Exception {
        try(var converter=mockConstruction(AppointmentConverter.class)) {
            try {ScheduleService.class.getMethod("updateAppointment"+field,Integer.class,AppointmentTo1.class).invoke(service,67,input);}
            catch(InvocationTargetException failure){if(failure.getCause()instanceof Exception cause)throw cause;throw failure;}
        }
    }
    private AppointmentTo1 request() {var input=new AppointmentTo1();input.setStatus("C");input.setType("FAKE-new-type");input.setUrgency("urgent");input.setName("FAKE-overposted-name");return input;}

    @ParameterizedTest @ValueSource(strings={"Status","Type","Urgency"})
    void shouldPreserveFieldAuditContract_throughRestAndLockedManager(String field) throws Exception {
        edit(field,request());
        String action="AppointmentManager.updateAppointment"+field;
        String patient=String.valueOf(TEST_DEMO_NO);
        logActionMock.verify(()->LogAction.addLogSynchronous(mockLoggedInInfo,action,LogConst.CON_APPT,"67",patient,"id=67"));
        logActionMock.verifyNoMoreInteractions();verify(archives).archiveAppointment(current);verify(appointments).merge(current);verify(appointments).flush();
        assertThat(current.getStatus()).isEqualTo(field.equals("Status")?"C":"t");assertThat(current.getType()).isEqualTo(field.equals("Type")?"FAKE-new-type":"original-type");assertThat(current.getUrgency()).isEqualTo(field.equals("Urgency")?"urgent":"original-urgency");assertThat(current.getName()).isEqualTo("FAKE-original-name");assertThat(current.getLastUpdateUser()).isEqualTo(TEST_PROVIDER);
    }
    @Test
    void shouldKeepGenericAudit_forExistingManagerCallers() {
        manager.updateAppointment(mockLoggedInInfo,67,null,row->row.setType("FAKE-new-type"));
        logActionMock.verify(()->LogAction.addLogSynchronous(mockLoggedInInfo,"AppointmentManager.updateAppointment",LogConst.CON_APPT,"67",String.valueOf(TEST_DEMO_NO),"appointmentId=67"));logActionMock.verifyNoMoreInteractions();
    }
    @Test
    void shouldDelegateNewOverload_forLegacyInterfaceImplementations() {
        ScheduleManager legacy=mock(ScheduleManager.class,CALLS_REAL_METHODS);
        java.util.function.Consumer<Appointment> change=row->row.setType("FAKE-new-type");
        when(legacy.updateAppointment(mockLoggedInInfo,67,null,change)).thenReturn(current);
        assertThat(legacy.updateAppointment(mockLoggedInInfo,67,null,ScheduleManager.AppointmentUpdateAction.TYPE,change)).isSameAs(current);
        verify(legacy).updateAppointment(mockLoggedInInfo,67,null,change);
    }
    @ParameterizedTest @ValueSource(strings={"Status","Type","Urgency"})
    void shouldRejectStaleEdit_withoutAnArchiveOrSuccessAudit(String field) {
        var input=request();input.setEditVersion("FAKE-stale");
        assertThatThrownBy(()->edit(field,input)).isInstanceOf(WebApplicationException.class).satisfies(error->assertThat(((WebApplicationException)error).getResponse().getStatus()).isEqualTo(409));
        verifyNoInteractions(archives);verify(appointments,never()).merge(any());logActionMock.verifyNoInteractions();assertThat(current.getStatus()).isEqualTo("t");
    }
    @ParameterizedTest @ValueSource(strings={"Status","Type","Urgency"})
    void shouldRejectUnauthorizedFieldEdit_beforeLockingOrAuditing(String field) {
        when(mockSecurityInfoManager.hasPrivilege(any(),eq("_appointment"),eq("w"),isNull())).thenReturn(false);
        assertThatThrownBy(()->edit(field,request())).isInstanceOf(WebApplicationException.class).satisfies(error->assertThat(((WebApplicationException)error).getResponse().getStatus()).isEqualTo(403));verifyNoInteractions(appointments,archives);logActionMock.verifyNoInteractions();
    }
    @ParameterizedTest @ValueSource(strings={"Status","Type","Urgency"})
    void shouldRejectMissingAppointment_withoutAnArchiveOrSuccessAudit(String field) {
        when(appointments.findForUpdate(67)).thenReturn(null);
        assertThatThrownBy(()->edit(field,request())).isInstanceOf(WebApplicationException.class)
                .satisfies(error->assertThat(((WebApplicationException)error).getResponse().getStatus()).isEqualTo(404));
        verifyNoInteractions(archives);verify(appointments,never()).merge(any());logActionMock.verifyNoInteractions();
    }
    @ParameterizedTest @ValueSource(strings={"Status","Type","Urgency"})
    void shouldKeepPatientRestrictions_whenFieldEditIsOtherwiseAuthorized(String field) {
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(mockLoggedInInfo,TEST_DEMO_NO)).thenReturn(false);
        assertThatThrownBy(()->edit(field,request())).isInstanceOf(WebApplicationException.class)
                .satisfies(error->assertThat(((WebApplicationException)error).getResponse().getStatus()).isEqualTo(403));
        verifyNoInteractions(archives);verify(appointments,never()).merge(any());logActionMock.verifyNoInteractions();
    }
    @ParameterizedTest @ValueSource(strings={"Status","Type","Urgency"})
    void shouldMapManagerMissingVersion_toTheExistingBadRequestResponse(String field) {
        ScheduleManager extension=mock(ScheduleManager.class);
        when(extension.updateAppointment(any(),eq(67),isNull(),any(ScheduleManager.AppointmentUpdateAction.class),any()))
                .thenThrow(new AppointmentUpdateException(AppointmentUpdateException.Reason.MISSING_VERSION));
        injectDependency(service,"scheduleManager",extension);
        assertThatThrownBy(()->edit(field,request())).isInstanceOf(WebApplicationException.class)
                .satisfies(error->assertThat(((WebApplicationException)error).getResponse().getStatus()).isEqualTo(400));
        verifyNoInteractions(appointments,archives);logActionMock.verifyNoInteractions();
    }
    @ParameterizedTest @ValueSource(strings={"current","archive","filtered"})
    void shouldKeepUnsupportedSyncDefaults_failClosedForExtensionImplementations(String operation) {
        ScheduleManager extension=mock(ScheduleManager.class,CALLS_REAL_METHODS);
        assertThatThrownBy(()->{
            switch(operation) {
                case "current"->extension.getAppointmentUpdatedPage(mockLoggedInInfo,new java.util.Date(0),0,10);
                case "archive"->extension.getAppointmentArchiveUpdatedPage(mockLoggedInInfo,new java.util.Date(0),0,10);
                case "filtered"->extension.getAppointmentsByProgramProviderDemographicPage(mockLoggedInInfo,1,TEST_PROVIDER,TEST_DEMO_NO,new java.util.Date(0),0,10);
                default->throw new AssertionError(operation);
            }
        }).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest @ValueSource(strings={"current","archive","filtered"})
    void shouldRetainSyncPageDispatchAndResults_forExistingManagerCallers(String operation) {
        current.setUpdateDateTime(new java.util.Date(1000));
        var archived=new io.github.carlos_emr.carlos.commn.model.AppointmentArchive();
        org.springframework.test.util.ReflectionTestUtils.setField(archived,"id",67);
        archived.setUpdateDateTime(new java.util.Date(1000));
        when(appointments.findByUpdateDateAndId(any(),anyInt(),anyInt())).thenReturn(List.of(current));
        when(archives.findByUpdateDateAndId(any(),anyInt(),anyInt())).thenReturn(List.of(archived));
        when(appointments.findByProgramProviderDemographicDateAndId(any(),any(),any(),any(),anyInt(),anyInt()))
                .thenReturn(List.of(current));
        switch(operation) {
            case "current"->assertThat(manager.getAppointmentUpdatedPage(mockLoggedInInfo,new java.util.Date(0),Integer.MAX_VALUE,10).items()).containsExactly(current);
            case "archive"->assertThat(manager.getAppointmentArchiveUpdatedPage(mockLoggedInInfo,new java.util.Date(0),Integer.MAX_VALUE,10).items()).containsExactly(archived);
            case "filtered"->assertThat(manager.getAppointmentsByProgramProviderDemographicPage(mockLoggedInInfo,1,TEST_PROVIDER,TEST_DEMO_NO,new java.util.Date(0),Integer.MAX_VALUE,10).items()).containsExactly(current);
            default->throw new AssertionError(operation);
        }
    }
    @ParameterizedTest @ValueSource(strings={"valid","null","invalid-type"})
    void shouldKeepConsentPolicyEnforcement_forExistingSyncCallers(String result) {
        current.setUpdateDateTime(new java.util.Date(1000));
        when(appointments.findByUpdateDateAndId(any(),anyInt(),anyInt())).thenReturn(List.of(current));
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo,"_appointment.UpdatedAfterDate","x",null)).thenReturn(false);
        var consent=mock(PatientConsentManager.class);injectDependency(manager,"patientConsentManager",consent);
        Object answer=switch(result) {
            case "valid"->List.of(current);
            case "null"->null;
            case "invalid-type"->List.of(mock(io.github.carlos_emr.carlos.commn.model.DemographicData.class));
            default->throw new AssertionError(result);
        };
        doReturn(answer).when(consent).filterProviderSpecificConsent(any(),anyList());
        if(result.equals("valid"))assertThat(manager.getAppointmentUpdatedPage(mockLoggedInInfo,new java.util.Date(0),Integer.MAX_VALUE,10).items()).containsExactly(current);
        else assertThatThrownBy(()->manager.getAppointmentUpdatedPage(mockLoggedInInfo,new java.util.Date(0),Integer.MAX_VALUE,10))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test
    void shouldRetainStrictProviderCountDateParsing_forExistingManagerCallers() {
        when(appointments.listProviderAppointmentCounts(any(),any())).thenReturn(List.of());
        assertThat(manager.listProviderAppointmentCounts(mockLoggedInInfo,"2026-08-01","2026-08-02")).isEmpty();
        assertThatThrownBy(()->manager.listProviderAppointmentCounts(mockLoggedInInfo,"2026-08-01","invalid"))
                .isInstanceOf(WebApplicationException.class);
    }
    @ParameterizedTest @ValueSource(strings={"day","period","history"})
    void shouldRetainReadPrivilegeRefusal_forExistingRestCallers(String operation) {
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo,"_appointment","r",(String)null)).thenReturn(false);
        assertThatThrownBy(()->{
            switch(operation) {
                case "day"->service.getAppointmentsForDay(TEST_PROVIDER,"2026-08-01");
                case "period"->service.listProviderApptsForPeriod(TEST_PROVIDER,"2026-08-01","2026-08-02");
                case "history"->service.findExistAppointments(TEST_DEMO_NO);
                default->throw new AssertionError(operation);
            }
        }).isInstanceOf(WebApplicationException.class).satisfies(error->
                assertThat(((WebApplicationException)error).getResponse().getStatus()).isEqualTo(403));
        verifyNoInteractions(appointments,archives);
    }
    @ParameterizedTest @ValueSource(strings={"null","missing-id","zero","negative"})
    void shouldKeepMalformedDeleteRefusal_forExistingRestCallers(String payload) {
        var input=payload.equals("null")?null:new AppointmentTo1();
        if(payload.equals("zero"))input.setId(0);
        if(payload.equals("negative"))input.setId(-1);
        assertThatThrownBy(()->service.deleteAppointment(input)).isInstanceOf(WebApplicationException.class)
                .satisfies(error->assertThat(((WebApplicationException)error).getResponse().getStatus()).isEqualTo(400));
        verifyNoInteractions(appointments,archives);logActionMock.verifyNoInteractions();
    }
    @Test
    void shouldRetainMissingAndExistingAppointmentResponses_forExistingRestCallers() {
        var reads=mock(AppointmentManager.class);injectDependency(service,"appointmentManager",reads);
        var input=new AppointmentTo1();input.setId(67);
        assertThatThrownBy(()->service.getAppointment(input)).isInstanceOf(jakarta.ws.rs.NotFoundException.class);
        when(reads.getAppointment(mockLoggedInInfo,67)).thenReturn(current);
        var dto=new AppointmentTo1();dto.setId(67);
        try(var converters=mockConstruction(AppointmentConverter.class,(converter,construction)->
                when(converter.getAsTransferObject(mockLoggedInInfo,current)).thenReturn(dto))) {
            assertThat(service.getAppointment(input).getAppointment()).isSameAs(dto);
        }
    }
    @Test
    void shouldRefuseMissingAddPayload_beforeCreatingAnAppointment() {
        assertThatThrownBy(()->service.addAppointment(null)).isInstanceOf(jakarta.ws.rs.BadRequestException.class);
        verifyNoInteractions(appointments,archives);logActionMock.verifyNoInteractions();
    }
    @Test
    void shouldRefuseRestrictedPatientAdd_beforeCreatingAnAppointment() {
        var input=new io.github.carlos_emr.carlos.webserv.rest.to.model.NewAppointmentTo1();input.setDemographicNo(TEST_DEMO_NO);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(mockLoggedInInfo,TEST_DEMO_NO)).thenReturn(false);
        assertThatThrownBy(()->service.addAppointment(input)).isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
        verifyNoInteractions(appointments,archives);logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest @ValueSource(strings={"Status","Type","Urgency"})
    void shouldRetainVersionedFieldEditSuccess_andArchiveBeforeMutation(String field) throws Exception {
        current.setUpdateDateTime(new java.util.Date(1000));
        var before=new java.util.concurrent.atomic.AtomicReference<List<String>>();
        doAnswer(call->{before.set(List.of(current.getEditVersion(),current.getStatus(),current.getType(),current.getUrgency()));return null;})
                .when(archives).archiveAppointment(current);
        doAnswer(call->{current.setUpdateDateTime(new java.util.Date(2000));return null;}).when(appointments).flush();
        var input=request();input.setEditVersion("1000");edit(field,input);
        assertThat(before.get()).containsExactly("1000","t","original-type","original-urgency");
        assertThat(current.getEditVersion()).isEqualTo("2000");
        logActionMock.verify(()->LogAction.addLogSynchronous(mockLoggedInInfo,"AppointmentManager.updateAppointment"+field,
                LogConst.CON_APPT,"67",String.valueOf(TEST_DEMO_NO),"id=67"));
        logActionMock.verifyNoMoreInteractions();
    }

}
