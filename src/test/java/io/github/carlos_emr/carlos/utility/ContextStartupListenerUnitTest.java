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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportMaintenanceService;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.commn.jobs.OscarJobUtils;
import io.github.carlos_emr.carlos.fax.core.FaxSchedulerJob;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMFixMissingReportHelper;
import jakarta.servlet.ServletContextEvent;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ContextStartupListenerUnitTest {
    @Test void shouldRetryFaxInitialization_onlyAfterTheGuardedClinicalStartupPhases() {
        var maintenance = mock(CdsImportMaintenanceService.class);
        var coordinator = mock(ClinicalStartupCoordinator.class);
        var fax = mock(FaxSchedulerJob.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        var listener = spy(new ContextStartupListener());
        doNothing().when(listener).initializeDefaultData(transactionManager);
        doNothing().when(listener).loadCaches();
        ReflectionTestUtils.setField(listener, "maintenance", maintenance);
        ReflectionTestUtils.setField(listener, "clinicalStartup", coordinator);
        try (var spring = mockStatic(SpringUtils.class);
                var jobs = mockStatic(OscarJobUtils.class);
                var hrm = mockConstruction(HRMFixMissingReportHelper.class)) {
            spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactionManager);
            spring.when(() -> SpringUtils.getBean(FaxSchedulerJob.class)).thenReturn(fax);
            ReflectionTestUtils.invokeMethod(listener, "initializeClinicalProcesses");
            verify(fax).retryDeferredInitialization();
            verify(hrm.constructed().getFirst()).fixIt();
            jobs.verify(OscarJobUtils::initializeJobExecutionFramework);
        }
    }

    @Test void shouldNotStartFax_whenMaintenanceReentersAtTheFinalStartupBoundary() {
        var maintenance = mock(CdsImportMaintenanceService.class);
        when(maintenance.isClinicalAccessBlocked()).thenReturn(false, false, false, false, true);
        var transactionManager = mock(PlatformTransactionManager.class);
        var listener = spy(new ContextStartupListener());
        doNothing().when(listener).initializeDefaultData(transactionManager);
        doNothing().when(listener).loadCaches();
        ReflectionTestUtils.setField(listener, "maintenance", maintenance);
        ReflectionTestUtils.setField(listener, "clinicalStartup", mock(ClinicalStartupCoordinator.class));
        try (var spring = mockStatic(SpringUtils.class);
                var jobs = mockStatic(OscarJobUtils.class);
                var hrm = mockConstruction(HRMFixMissingReportHelper.class)) {
            spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactionManager);
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(listener, "initializeClinicalProcesses"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Clinical startup deferred");
            spring.verify(() -> SpringUtils.getBean(FaxSchedulerJob.class), never());
        }
    }
    @Test void shouldBootRecoveryContextWithoutClinicalQueries_whenMaintenanceIsRetained() {
        var maintenance = mock(CdsImportMaintenanceService.class);
        when(maintenance.isClinicalAccessBlocked()).thenReturn(true);
        var context = new MockServletContext();
        var listener = new ContextStartupListener();
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(CdsImportMaintenanceService.class)).thenReturn(maintenance);
            listener.contextInitialized(new ServletContextEvent(context));
            var coordinator = (ClinicalStartupCoordinator) context.getAttribute(ClinicalStartupCoordinator.ATTRIBUTE);
            assertThat(coordinator.state()).isEqualTo(ClinicalStartupCoordinator.State.PENDING);
            // Applies even to an empty database without default program/site: no fabricated rows or IDs.
            spring.verify(() -> SpringUtils.getBean(CdsImportMaintenanceService.class));
            spring.verifyNoMoreInteractions();
            listener.contextDestroyed(new ServletContextEvent(context));
            assertThat(coordinator.state()).isEqualTo(ClinicalStartupCoordinator.State.CLOSED);
        } finally { MiscUtils.setShutdownSignaled(false); }
    }

    @Test void shouldRollbackAllDefaultCreation_whenSiteCreationFails() {
        var programDao = mock(ProgramDao.class);
        var providerDao = mock(ProgramProviderDAO.class);
        var roleDao = mock(SecroleDao.class);
        var facilityDao = mock(FacilityDao.class);
        var siteDao = mock(SiteDao.class);
        var providerSiteDao = mock(ProviderSiteDao.class);
        var program = new Program(); program.setId(10);
        var facility = new Facility(); facility.setId(1);
        var role = new Secrole(); role.setId(1L);
        when(programDao.getProgramByName("OSCAR")).thenReturn(null, program);
        when(facilityDao.findAll(null)).thenReturn(List.of(facility));
        when(roleDao.getRoleByName("doctor")).thenReturn(role);
        when(siteDao.getAllSites()).thenReturn(List.of());
        doAnswer(call -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); return null; })
                .when(programDao).saveProgram(any());
        doAnswer(call -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); return null; })
                .when(providerDao).saveProgramProvider(any());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw new IllegalStateException("new hold before site write");
        }).when(siteDao).persist(any());
        var transactionManager = new RecordingTransactionManager();
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(ProgramDao.class)).thenReturn(programDao);
            spring.when(() -> SpringUtils.getBean(ProgramProviderDAO.class)).thenReturn(providerDao);
            spring.when(() -> SpringUtils.getBean(SecroleDao.class)).thenReturn(roleDao);
            spring.when(() -> SpringUtils.getBean(FacilityDao.class)).thenReturn(facilityDao);
            spring.when(() -> SpringUtils.getBean(SiteDao.class)).thenReturn(siteDao);
            spring.when(() -> SpringUtils.getBean(ProviderSiteDao.class)).thenReturn(providerSiteDao);
            assertThatThrownBy(() -> new ContextStartupListener().initializeDefaultData(transactionManager))
                    .isInstanceOf(IllegalStateException.class);
        }
        verify(programDao).saveProgram(any()); verify(providerDao).saveProgramProvider(any());
        assertThat(transactionManager.rollbacks).isEqualTo(1);
        assertThat(transactionManager.commits).isZero();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits; int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
