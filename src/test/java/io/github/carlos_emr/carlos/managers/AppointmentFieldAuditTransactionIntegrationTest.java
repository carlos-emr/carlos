/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.*;

/** Reads committed state after audit lookup fails before an audit DAO joins the transaction. */
@Tag("integration")
@Isolated("Actual LogAction DAO cache")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=ScheduleLegacySyncIntegrationTest.Config.class)
class AppointmentFieldAuditTransactionIntegrationTest {
    @Autowired DataSource source;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactions;
    private JdbcTemplate jdbc;
    private ScheduleManager manager;
    private LoggedInInfo actor;
    private Field auditCache;
    private Object previousAudit;

    @BeforeEach
    void prepare() throws Exception {
        jdbc=new JdbcTemplate(source);jdbc.update("DELETE FROM appointmentArchive");jdbc.update("DELETE FROM appointment");
        jdbc.update("INSERT INTO appointment (appointment_no,provider_no,appointment_date,start_time,end_time,demographic_no,program_id,status,type,urgency,name,createdatetime,updatedatetime) VALUES (67,'FAKE','2001-09-09','09:00:00','09:15:00',42,0,'t','FAKE-type','normal','FAKE-name','2001-09-09 09:00:00','2001-09-09 09:00:00')");
        var em=SharedEntityManagerCreator.createSharedEntityManager(factory);
        var appointments=new OscarAppointmentDaoImpl();ReflectionTestUtils.setField(appointments,"entityManager",em);
        var archives=new AppointmentArchiveDaoImpl();ReflectionTestUtils.setField(archives,"entityManager",em);
        var security=mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(),eq("_appointment"),eq("w"),isNull())).thenReturn(true);
        actor=mock(LoggedInInfo.class);when(actor.getLoggedInProviderNo()).thenReturn("FAKE");
        when(security.hasPrivilege(actor,"_appointment","w","42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(actor,42)).thenReturn(true);
        var target=new ScheduleManagerImpl();ReflectionTestUtils.setField(target,"oscarAppointmentDao",appointments);ReflectionTestUtils.setField(target,"appointmentArchiveDao",archives);ReflectionTestUtils.setField(target,"securityInfoManager",security);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));manager=(ScheduleManager)proxy.getProxy();
        auditCache=LogAction.class.getDeclaredField("oscarLogDao");auditCache.setAccessible(true);previousAudit=auditCache.get(null);auditCache.set(null,null);
    }
    @AfterEach
    void restore() throws Exception {if(auditCache!=null)auditCache.set(null,previousAudit);}

    private List<java.util.Map<String,Object>> committedAppointment() {
        return jdbc.queryForList("SELECT status,type,urgency,name,lastupdateuser,updatedatetime FROM appointment WHERE appointment_no=67");
    }
    @ParameterizedTest
    @EnumSource(value=ScheduleManager.AppointmentUpdateAction.class,names={"STATUS","TYPE","URGENCY"})
    void shouldRollBackFieldAndArchive_whenAuditDaoLookupFails(ScheduleManager.AppointmentUpdateAction action) {
        var before=committedAppointment();var failure=new IllegalStateException("FAKE audit DAO unavailable");
        Throwable observed;
        try(var spring=mockStatic(SpringUtils.class)) {
            spring.when(()->SpringUtils.getBean(OscarLogDao.class)).thenThrow(failure);
            observed=catchThrowable(()->manager.updateAppointment(actor,67,null,action,row->{
                switch(action) {case STATUS->row.setStatus("C");case TYPE->row.setType("FAKE-replacement");case URGENCY->row.setUrgency("urgent");default->throw new AssertionError(action);}
            }));
            spring.verify(()->SpringUtils.getBean(OscarLogDao.class));
        }
        var after=committedAppointment();var archives=jdbc.queryForObject("SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=67",Integer.class);
        assertAll(()->assertThat(observed).as("audit failure propagates out of the transaction").isSameAs(failure),
                ()->assertThat(after).as("committed appointment after audit failure").isEqualTo(before),
                ()->assertThat(archives).as("committed archives after audit failure").isZero());
    }
}
