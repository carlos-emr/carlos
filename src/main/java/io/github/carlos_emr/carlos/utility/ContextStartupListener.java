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
 */

package io.github.carlos_emr.carlos.utility;

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
import io.github.carlos_emr.carlos.PMmodule.utility.ProgramAccessCache;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.commn.jobs.OscarJobUtils;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMFixMissingReportHelper;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.CidPrefixResourceResolver;

import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.CarlosProperties;

/**
 * Servlet context listener that initializes the CARLOS EMR application on startup.
 *
 * <p>Performs essential initialization tasks including: configuring logging, registering
 * the shutdown hook, creating the default OSCAR program and site if missing, starting
 * the job execution framework, loading caches, and running the HRM fix helper.
 *
 * @since 2026-03-17
 */
public class ContextStartupListener implements jakarta.servlet.ServletContextListener {
    private static final Logger logger = MiscUtils.getLogger();
    private static final CarlosProperties oscarProperties = CarlosProperties.getInstance();

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

            LocaleUtils.BASE_NAME = "oscarResources";

            MiscUtils.setShutdownSignaled(false);
            MiscUtils.registerShutdownHook();

            createOscarProgramIfNecessary();
            createDefaultSiteIfNecessary();

            OscarJobUtils.initializeJobExecutionFramework();


            if (oscarProperties.isPropertyActive("encrypted_xml.remove_cid_prefix")) {
                ResourceResolver.register(new CidPrefixResourceResolver(), true);
            }

            //Run some optimizations
            loadCaches();

            logger.info("OSCAR server processes started. context=" + contextPath);

            //bug 4195 - only runs once so long as it finishes..if you want it to not run, add entry
            //try your property table called "HRMFixMissingReportHelper.Run" with value = 1
            HRMFixMissingReportHelper hrmFixer = new HRMFixMissingReportHelper();
            try {
                hrmFixer.fixIt();
            } catch (Exception e) {
                logger.error("Error running HRM fixer", e);
            }
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            throw (new RuntimeException(e));
        }
    }

    /**
     * Loads program access caches and role caches for all active programs.
     */
    public void loadCaches() {
        ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
        for (Program program : programDao.getActivePrograms()) {
            ProgramAccessCache.setAccessMap(program.getId().longValue());
        }
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
        logger.info("Server processes stopping. context=" + sce.getServletContext().getContextPath());

        try {
            MiscUtils.checkShutdownSignaled();
            MiscUtils.deregisterShutdownHook();
            MiscUtils.setShutdownSignaled(true);
        } catch (ShutdownException e) {
            // do nothing it's okay.
        }
    }
}
