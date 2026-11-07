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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsFreshAuthorizationReadAspect;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Reproduces a real stale view-context account while another physical transaction revokes it. */
@Tag("integration") @Tag("cds-export")
class CdsExportFreshAuthorizationIntegrationTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass = true) @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config { }
    @Test void shouldSeeRevocation_withoutClearingTheRequestsPreexistingManagedEntities() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            DataSource source = new DriverManagerDataSource("jdbc:h2:mem:fresh_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
            context.register(Config.class); context.registerBean("dataSource", DataSource.class, () -> source);
            context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, () -> {
                var factory = new LocalContainerEntityManagerFactoryBean(); factory.setPersistenceUnitName("entityManagerFactory");
                factory.setDataSource(source); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                factory.setManagedTypes(PersistenceManagedTypes.of(Provider.class.getName(), Security.class.getName()));
                factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.dialect", "org.hibernate.dialect.H2Dialect")); return factory;
            });
            context.registerBean("transactionManager", PlatformTransactionManager.class, () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
            context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
            context.registerBean(CdsExportConfigurationService.class, () -> new CdsExportConfigurationService(new Properties()));
            context.registerBean(SecurityInfoManager.class, () -> {
                var security = mock(SecurityInfoManager.class);
                when(security.hasPrivilegeReadOnly(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true); return security;
            });
            context.registerBean(ProviderDao.class, () -> {
                var providers = mock(ProviderDao.class); var shared = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
                when(providers.getProvider(anyString())).thenAnswer(call -> shared.find(Provider.class, (String) call.getArgument(0))); return providers;
            });
            context.registerBean(SecurityDao.class, () -> {
                var accounts = mock(SecurityDao.class); var shared = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
                when(accounts.find(anyInt())).thenAnswer(call -> shared.find(Security.class, (Integer) call.getArgument(0))); return accounts;
            });
            context.registerBean(ProviderFacilityDao.class, () -> mock(ProviderFacilityDao.class));
            context.registerBean(io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao.class,
                    () -> mock(io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao.class));
            context.register(CdsFreshAuthorizationReadAspect.class, CdsExportAccessService.class); context.refresh();
            var factory = context.getBean(EntityManagerFactory.class); int accountId;
            try (var setup = factory.createEntityManager()) {
                setup.getTransaction().begin(); var provider = new Provider(); provider.setProviderNo("100"); provider.setStatus("1");
                provider.setFirstName("FAKE-CDS"); provider.setLastName("Authorization"); provider.setProviderType("doctor"); provider.setSex("F"); provider.setSpecialty("");
                setup.persist(provider); var account = new Security(); account.setUserName("FAKE-CDS-AUTH"); account.setPassword("synthetic");
                account.setProviderNo("100"); account.setBExpireset(0); setup.persist(account); setup.flush(); accountId = account.getSecurityNo(); setup.getTransaction().commit();
            }
            try (var view = factory.createEntityManager()) {
                var oldAccount = view.find(Security.class, accountId); var provider = view.find(Provider.class, "100");
                var viewer = new LoggedInInfo(); viewer.setLoggedInSecurity(oldAccount); viewer.setLoggedInProvider(provider);
                var holder = new EntityManagerHolder(view); TransactionSynchronizationManager.bindResource(factory, holder);
                try {
                    var access = context.getBean(CdsExportAccessService.class); assertThat(access.operatorSession(viewer).securityNo()).isEqualTo(accountId);
                    assertThat(TransactionSynchronizationManager.getResource(factory)).isSameAs(holder);
                    try (var changed = factory.createEntityManager()) {
                        changed.getTransaction().begin(); var revoked = changed.find(Security.class, accountId); revoked.setBExpireset(1);
                        revoked.setDateExpiredate(java.util.Date.from(Instant.parse("2000-01-01T00:00:00Z"))); changed.getTransaction().commit();
                    }
                    // The request's original context still demonstrates the stale pre-revocation row.
                    assertThat(view.find(Security.class, accountId).getBExpireset()).isZero();
                    assertThatThrownBy(() -> access.operatorSession(viewer)).isInstanceOf(SecurityException.class);
                    assertThat(TransactionSynchronizationManager.getResource(factory)).isSameAs(holder);
                    assertThat(view.contains(oldAccount)).isTrue(); assertThat(view.contains(provider)).isTrue();
                } finally { TransactionSynchronizationManager.unbindResource(factory); }
            }
        }
    }
}
