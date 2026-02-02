//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.utility;

import ca.openosp.openo.commn.dao.ProviderSiteDao;
import ca.openosp.openo.commn.dao.SiteDao;
import ca.openosp.openo.commn.model.ProviderSite;
import ca.openosp.openo.commn.model.ProviderSitePK;
import ca.openosp.openo.commn.model.Site;
import org.apache.logging.log4j.Logger;
import org.apache.xml.security.utils.resolver.ResourceResolver;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorUpdateTask;
import ca.openosp.openo.PMmodule.dao.ProgramDao;
import ca.openosp.openo.PMmodule.dao.ProgramProviderDAO;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.utility.ProgramAccessCache;
import ca.openosp.openo.PMmodule.utility.RoleCache;
import ca.openosp.openo.commn.jobs.OscarJobUtils;
import ca.openosp.openo.hospitalReportManager.HRMFixMissingReportHelper;
import ca.openosp.openo.integration.mcedt.mailbox.CidPrefixResourceResolver;

import ca.openosp.openo.daos.security.SecroleDao;
import ca.openosp.OscarProperties;

public class ContextStartupListener implements javax.servlet.ServletContextListener {
    private static final Logger logger = MiscUtils.getLogger();
    private static final OscarProperties oscarProperties = OscarProperties.getInstance();

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        // ensure cxf uses log4j2
        System.setProperty("org.apache.cxf.Logger", "org.apache.cxf.commons.logging.Log4j2Logger");

        /*
         * Map log4j version 1 to version 2
         */
        System.setProperty("log4j1.compatibility", "true");

        // Disable unsafe serialization in commons-collections to prevent CVE-2015-7501
        System.setProperty("org.apache.commons.collections.enableUnsafeSerialization", "false");
        logger.info("Commons-collections unsafe serialization disabled for CVE-2015-7501 protection");

        try {
            String contextPath = sce.getServletContext().getContextPath();

            logger.info("Starting OSCAR context. context=" + contextPath);

            MiscUtils.addLoggingOverrideConfiguration(contextPath);

            LocaleUtils.BASE_NAME = "oscarResources";

            MiscUtils.setShutdownSignaled(false);
            MiscUtils.registerShutdownHook();

            createOscarProgramIfNecessary();
            createDefaultSiteIfNecessary();

            if (oscarProperties.getBooleanProperty("INTEGRATOR_ENABLED", "true")) {
                CaisiIntegratorUpdateTask.startTask();
            }

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

        Program p = programDao.getProgramByName("OSCAR");
        if (p != null)
            return;
        p = new Program();
        p.setFacilityId(1);
        p.setName("OSCAR");
        p.setMaxAllowed(99999);
        p.setType("Service");
        p.setProgramStatus("active");
        programDao.saveProgram(p);

        ProgramProvider pp = new ProgramProvider();
        pp.setProviderNo("999998");
        pp.setProgramId(p.getId().longValue());
        pp.setRoleId(secRoleDao.getRoleByName("doctor").getId());
        programProviderDao.saveProgramProvider(pp);

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
        defaultSite.setStatus((byte)1);
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
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        logger.info("Server processes stopping. context=" + sce.getServletContext().getContextPath());

        CaisiIntegratorUpdateTask.stopTask();

        try {
            MiscUtils.checkShutdownSignaled();
            MiscUtils.deregisterShutdownHook();
            MiscUtils.setShutdownSignaled(true);
        } catch (ShutdownException e) {
            // do nothing it's okay.
        }
    }
}
