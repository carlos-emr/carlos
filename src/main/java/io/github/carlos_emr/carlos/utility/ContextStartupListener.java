/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 * Modifications by CARLOS Contributors, 2026: defer clinical bootstrap during retained import maintenance.
 */

package io.github.carlos_emr.carlos.utility;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.ProviderSitePK;
import io.github.carlos_emr.carlos.commn.model.Site;
import org.apache.logging.log4j.Logger;
import org.apache.xml.security.utils.resolver.ResourceResolver;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.commn.jobs.OscarJobUtils;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMFixMissingReportHelper;
import io.github.carlos_emr.carlos.hospitalReportManager.HrmReportRepairDeferredException;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.CidPrefixResourceResolver;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteCodeSweeper;

import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportMaintenanceService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class ContextStartupListener implements jakarta.servlet.ServletContextListener {
    private static final Logger logger = MiscUtils.getLogger();
    private static final CarlosProperties oscarProperties = CarlosProperties.getInstance();

    // Both guarded by this: the clinical startup thread schedules, the container thread cancels in contextDestroyed.
    private ScheduledFuture<?> portalInviteSweep;
    private boolean sweepsStopped;
    private ClinicalStartupCoordinator clinicalStartup;
    private CdsImportMaintenanceService maintenance;

    @Override
    public void contextInitialized(jakarta.servlet.ServletContextEvent sce) {

        // ensure cxf uses log4j2
        System.setProperty("org.apache.cxf.Logger", "org.apache.cxf.commons.logging.Log4j2Logger");

        /*
         * Map log4j version 1 to version 2
         */
        System.setProperty("log4j1.compatibility", "true");


        try {
            String contextPath = sce.getServletContext().getContextPath();

            logger.info("Starting OSCAR context. context=" + contextPath);

            MiscUtils.addLoggingOverrideConfiguration(contextPath);

            MiscUtils.setShutdownSignaled(false);
            MiscUtils.registerShutdownHook();

            if (oscarProperties.isPropertyActive("encrypted_xml.remove_cid_prefix")) {
                ResourceResolver.register(new CidPrefixResourceResolver(), true);
            }
            maintenance = SpringUtils.getBean(CdsImportMaintenanceService.class);
            clinicalStartup = new ClinicalStartupCoordinator(maintenance::isClinicalAccessBlocked,
                    this::initializeClinicalProcesses);
            sce.getServletContext().setAttribute(ClinicalStartupCoordinator.ATTRIBUTE, clinicalStartup);
            clinicalStartup.start();
            // OscarSpringContextLoader installs SpringUtils only after Spring refresh.
            // Start durable recovery here, including when retained maintenance leaves
            // ordinary clinical initialization pending. Earlier worker execution can
            // mistake an uninitialized privilege lookup for revoked authorization.
            SpringUtils.getBean(CdsImportService.class).start();
            startCdsExport();
            logger.info("OSCAR web context started. context={} clinicalStartup={}", contextPath, clinicalStartup.state());
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            throw (new RuntimeException(e));
        }
    }

    /**
     * Start cleanup independently of invitation traffic; cancel it when this webapp stops. Safe to call again:
     * the coordinator can run the whole clinical initialization a second time (maintenance returning right after it,
     * or a failed gate read), and after shutdown has begun nothing is scheduled.
     */
    private void forgetLeftoverPortalInviteCodes() {
        synchronized (this) { if (sweepsStopped || portalInviteSweep != null) return; }
        try {
            PortalInviteCodeSweeper sweeper = new PortalInviteCodeSweeper(SpringUtils.getBean(EmailLogDao.class));
            sweeper.run();
            ScheduledFuture<?> scheduled = SpringUtils.getBean(TaskScheduler.class).scheduleWithFixedDelay(
                    sweeper, Instant.now().plus(PortalInviteCodeSweeper.INTERVAL), PortalInviteCodeSweeper.INTERVAL);
            // Shutdown or another run may have got here while the first pass ran: keep exactly one live timer.
            synchronized (this) {
                if (sweepsStopped || portalInviteSweep != null) scheduled.cancel(false);
                else portalInviteSweep = scheduled;
            }
        } catch (RuntimeException e) {
            logger.warn("patient portal invitation code sweep could not be scheduled: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Starts the CDS export dispatcher without letting it decide whether the EMR deploys.
     * Export is optional and off by default; a failure to start it leaves the dispatcher
     * stopped, which the export status page already reports, while clinical use continues.
     */
    static void startCdsExport() {
        try {
            SpringUtils.getBean(io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportService.class).start();
        } catch (RuntimeException exportUnavailable) {
            logger.error("CDS_EXPORT_STARTUP_FAILED dispatcher left stopped {}",
                    LogSafe.exceptionTrace(exportUnavailable));
        }
    }

    private void initializeClinicalProcesses() throws Exception {
        requireClinicalAccessOpen();
        // All default rows/links commit together. A new maintenance owner must drain this
        // ordinary transaction before closing admission, rather than leaving half a default.
        initializeDefaultData(SpringUtils.getBean(PlatformTransactionManager.class));
        requireClinicalAccessOpen();
        loadCaches();
        requireClinicalAccessOpen();
        OscarJobUtils.initializeJobExecutionFramework();
        requireClinicalAccessOpen();
        // Preserve the existing best-effort migration contract for genuine fixer errors.
        // An actual maintenance transition instead leaves initialization pending for retry.
        try { new HRMFixMissingReportHelper().fixIt(); }
        catch (HrmReportRepairDeferredException deferred) { throw deferred; }
        catch (Exception failure) {
            requireClinicalAccessOpen();
            logger.error("HRM startup fixer failed ({}); completion flag is unchanged", failure.getClass().getName());
        }
        requireClinicalAccessOpen();
        SpringUtils.getBean(io.github.carlos_emr.carlos.fax.core.FaxSchedulerJob.class)
                .retryDeferredInitialization();
        // After clinical initialization, so it is held back with the other clinical processes while maintenance is
        // retained, as it was before startup moved into the coordinator. This initialization can run more than once
        // (the coordinator retries it), so the call is idempotent.
        forgetLeftoverPortalInviteCodes();
    }

    void initializeDefaultData(PlatformTransactionManager transactionManager) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createOscarProgramIfNecessary();
            createDefaultSiteIfNecessary();
        });
    }

    private void requireClinicalAccessOpen() {
        if (Thread.currentThread().isInterrupted() || clinicalStartup.state() == ClinicalStartupCoordinator.State.CLOSED) {
            throw new IllegalStateException("Clinical startup stopped");
        }
        if (maintenance.isClinicalAccessBlocked()) throw new IllegalStateException("Clinical startup deferred");
    }

    public void loadCaches() {
        // ProgramAccessCache already loads each real access map on a cache miss.
        // Avoid eagerly materializing every Program entity/formula and access map here.
        RoleCache.reload();
    }


    private void createOscarProgramIfNecessary() {
        ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
        SecroleDao secRoleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        FacilityDao facilityDao = (FacilityDao) SpringUtils.getBean(FacilityDao.class);

        Program p = programDao.getProgramByName("OSCAR");
        if (p != null)
            return;

        // Ensure a default Facility exists before creating the program
        Integer facilityId = ensureDefaultFacilityExists(facilityDao);

        p = new Program();
        p.setFacilityId(facilityId);
        p.setName("OSCAR");
        p.setMaxAllowed(99999);
        p.setType("Service");
        p.setProgramStatus("active");
        programDao.saveProgram(p);

        // Re-fetch to get the generated ID (merge() doesn't update the passed object)
        p = programDao.getProgramByName("OSCAR");

        ProgramProvider pp = new ProgramProvider();
        pp.setProviderNo("999998");
        pp.setProgramId(p.getId().longValue());
        pp.setRoleId(secRoleDao.getRoleByName("doctor").getId());
        programProviderDao.saveProgramProvider(pp);

    }

    private Integer ensureDefaultFacilityExists(FacilityDao facilityDao) {
        java.util.List<Facility> facilities = facilityDao.findAll(null);
        if (facilities != null && !facilities.isEmpty()) {
            return facilities.get(0).getId();
        }

        Facility facility = new Facility();
        facility.setName("Default Facility");
        facility.setDisabled(false);
        facility.setOrgId(0);
        facility.setSectorId(0);
        facilityDao.persist(facility);
        logger.info("Created default Facility (ID: " + facility.getId() + ")");
        return facility.getId();
    }

    private void createDefaultSiteIfNecessary() {
        SiteDao siteDao = SpringUtils.getBean(SiteDao.class);
        ProviderSiteDao providerSiteDao = SpringUtils.getBean(ProviderSiteDao.class);
        
        java.util.List<Site> sites = siteDao.getAllSites();
        if (!sites.isEmpty()) {
            return;
        }
        
        // Create default site
        Site defaultSite = new Site();
        defaultSite.setName("Main Clinic");
        defaultSite.setShortName("MAIN");
        defaultSite.setBgColor("white");
        defaultSite.setStatus((byte) 1);
        siteDao.persist(defaultSite);
        
        // Link default providers (999998) to the site (if not already linked)
        ProviderSitePK psId = new ProviderSitePK();
        psId.setProviderNo("999998");
        psId.setSiteId(defaultSite.getSiteId());
        
        ProviderSite existingPS = providerSiteDao.find(psId);
        if (existingPS == null) {
            ProviderSite ps = new ProviderSite();
            ps.setId(psId);
            providerSiteDao.persist(ps);
        }
        
        logger.info("Created default site: " + defaultSite.getName() + " (ID: " + defaultSite.getSiteId() + ")");
    }

    @Override
    public void contextDestroyed(jakarta.servlet.ServletContextEvent sce) {
        synchronized (this) {
            sweepsStopped = true;
            if (portalInviteSweep != null) {
                portalInviteSweep.cancel(false);
            }
        }
        logger.info("Server processes stopping. context=" + sce.getServletContext().getContextPath());

        if (clinicalStartup != null) clinicalStartup.close();
        sce.getServletContext().removeAttribute(ClinicalStartupCoordinator.ATTRIBUTE);
        try {
            MiscUtils.checkShutdownSignaled();
            MiscUtils.deregisterShutdownHook();
            MiscUtils.setShutdownSignaled(true);
        } catch (ShutdownException e) {
            // do nothing it's okay.
        }
    }
}
