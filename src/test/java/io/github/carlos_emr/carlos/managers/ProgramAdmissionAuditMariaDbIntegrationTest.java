/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManagerImpl;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.ProgramService;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.util.List;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Real resource transaction, AdmissionManager and synchronous audit sink; admission selection is isolated. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=ProgramAdmissionAuditMariaDbIntegrationTest.Config.class)
@Tag("integration") @Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named="CARLOS_TEST_MARIADB_DAO",matches="true")
class ProgramAdmissionAuditMariaDbIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactions;
    private ProgramService resource;
    private SecurityInfoManager security;
    private LoggedInInfo actor;
    private Object priorAudit;
    private MockedStatic<SpringUtils> spring;
    @BeforeEach void setup(){
        new JdbcTemplate(dataSource).update("DELETE FROM log");
        var em=SharedEntityManagerCreator.createSharedEntityManager(factory);var audit=new OscarLogDaoImpl();ReflectionTestUtils.setField(audit,"entityManager",em);
        priorAudit=ReflectionTestUtils.getField(LogAction.class,"oscarLogDao");ReflectionTestUtils.setField(LogAction.class,"oscarLogDao",audit);
        actor=mock(LoggedInInfo.class);when(actor.getLoggedInProviderNo()).thenReturn("FAKE");when(actor.getLoggedInProvider()).thenReturn(new Provider("FAKE","Fixture","doctor","F","","Clinician"));
        security=mock(SecurityInfoManager.class);when(security.hasPrivilege(actor,"_pmm_management","r",(String)null)).thenReturn(true);
        when(security.hasPrivilegeReadOnly(eq(actor),anyString(),eq("r"),eq("42"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(actor,42)).thenReturn(true);
        List<Admission> rows=IntStream.rangeClosed(1,501).mapToObj(id->{var row=new Admission();row.setId((long)id);row.setClientId(id<=500?42:43);return row;}).toList();
        var selections=mock(AdmissionDao.class);
        when(selections.findAdmissionsByProgramAndDate(eq(7),any(),anyInt(),eq(500))).thenAnswer(call->{
            em.unwrap(Session.class).doWork(connection->{assertThat(connection.getMetaData().getDatabaseProductVersion()).contains("MariaDB");assertThat(connection.isReadOnly()).isFalse();assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);});
            int offset=call.getArgument(2);return rows.subList(offset,Math.min(rows.size(),offset+500));
        });
        var manager=new AdmissionManagerImpl();ReflectionTestUtils.setField(manager,"dao",selections);
        var api=new ProgramService(mock(ProgramManager2.class),manager,security){@Override protected LoggedInInfo getLoggedInInfo(){return actor;}};
        var proxy=new ProxyFactory(api);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));resource=(ProgramService)proxy.getProxy();
        spring=mockStatic(SpringUtils.class);var patients=mock(DemographicManager.class);var patient=new Demographic();patient.setDemographicNo(42);patient.setFirstName("Fixture");patient.setLastName("Patient");patient.setYearOfBirth("2001");patient.setMonthOfBirth("09");patient.setDateOfBirth("09");
        when(patients.getDemographic(actor,42)).thenReturn(patient);spring.when(()->SpringUtils.getBean(DemographicManager.class)).thenReturn(patients);
    }
    @AfterEach void restore(){ReflectionTestUtils.setField(LogAction.class,"oscarLogDao",priorAudit);if(spring!=null)spring.close();}
    private int audits(){return new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM log WHERE action='AdmissionManager.findAdmissionsByProgramAndDate'",Integer.class);}
    @Test void shouldCommitSynchronousAdmissionAudits_withThePatientFilteredResponse()throws Exception{
        var result=resource.getPatientList("7","2026-11-09",0,1);assertThat(result.getTotal()).isEqualTo(500);assertThat(result.getContent()).hasSize(1);assertThat(audits()).isEqualTo(501);
    }
    @Test void shouldRollbackEarlierReadAudits_whenLaterPatientPolicyAbortsTheResponse(){
        when(security.hasPrivilegeReadOnly(actor,"_pmm_management","r","43")).thenThrow(new IllegalStateException("Synthetic policy failure"));
        assertThatThrownBy(()->resource.getPatientList("7","2026-11-09",0,1)).isInstanceOf(IllegalStateException.class);assertThat(audits()).isZero();
    }
    @Configuration static class Config{
        @Bean(destroyMethod="close")IsolatedJpaTestDatabase ownedDatabase()throws Exception{return new IsolatedJpaTestDatabase("admission_audit");}
        @Bean DataSource dataSource(IsolatedJpaTestDatabase db){return db.dataSource();}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source,IsolatedJpaTestDatabase db){var bean=new LocalContainerEntityManagerFactoryBean();bean.setDataSource(source);bean.setManagedTypes(PersistenceManagedTypes.of(OscarLog.class.getName()));bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());bean.setJpaPropertyMap(db.jpaProperties());return bean;}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory){return new JpaTransactionManager(factory);}
    }
}
