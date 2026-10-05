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
package io.github.carlos_emr.carlos.commn.jobs;

import java.io.IOException;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Scheduled refresh of the local vaccine catalogue from the National Vaccine Catalogue.
 *
 * <p>An administrator adds a job type with this class name under Administration, then schedules
 * a job of that type, which runs as the provider it is set up with. A failed refresh is logged and
 * leaves the stored catalogue unchanged.</p>
 *
 * @since 2026-10-05
 */
public class CanadianVaccineCatalogueJob implements OscarRunnable {

    private static final Logger logger = MiscUtils.getLogger();

    private Provider provider;
    private Security security;

    @Override
    public void run() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLoggedInSecurity(security);
        try {
            SpringUtils.getBean(CanadianVaccineCatalogueManager.class).update(loggedInInfo);
        } catch (IOException | RuntimeException e) {
            logger.error("Vaccine catalogue update failed; the stored catalogue is unchanged", e);
        }
    }

    @Override
    public void setLoggedInProvider(Provider provider) {
        this.provider = provider;
    }

    @Override
    public void setLoggedInSecurity(Security security) {
        this.security = security;
    }

    @Override
    public void setConfig(String config) {
        // No configuration: the catalogue address is fixed.
    }
}
