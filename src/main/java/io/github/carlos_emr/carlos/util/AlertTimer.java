/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.util;

import java.util.Timer;
import java.util.TimerTask;

import io.github.carlos_emr.carlos.billings.ca.bc.MSP.CDMReminderHlp;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.ShutdownException;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Manages the scheduled execution of CDM (Chronic Disease Management) reminder alerts
 * at regular intervals using a {@link Timer}. Implemented as a singleton that triggers
 * {@link CDMReminderHlp#manageCDMTicklers} on a fixed-rate schedule to create and
 * manage ticklers for chronic disease follow-ups.
 *
 * @since 2001-01-01
 */
public class AlertTimer {
    private static Logger logger = MiscUtils.getLogger();

    private static AlertTimer alerts = null;
    private static Timer timer;
    String alertCodes[] = null;
    CDMReminderHlp hlp = null;

    private AlertTimer(String[] codes, long interval) {
        timer = new Timer("AlertTimer", true);
        alertCodes = codes;
        hlp = new CDMReminderHlp();
        //triggers alerts 5 seconds after instantiation
        timer.scheduleAtFixedRate(new ReminderClass(), 5000, interval);
    }

    /**
     * Returns the singleton AlertTimer instance, creating it if necessary.
     * The timer begins executing alerts 5 seconds after creation.
     *
     * @param codes String[] the CDM alert codes to process
     * @param interval long the interval in milliseconds between alert executions
     * @return AlertTimer the singleton instance
     */
    public static AlertTimer getInstance(String[] codes, long interval) {
        if (alerts == null) {
            alerts = new AlertTimer(codes, interval);
        }
        return alerts;
    }

    /**
     * The helper class which is responsible for triggering the alerts
     */
    class ReminderClass extends TimerTask {
        public void run() {
            // LoggedInInfo loggedInInfo=LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();
            // work around for the sec object.
            String providerNo = "-1";
            ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
            LoggedInInfo loggedInInfo = new LoggedInInfo();
            Security security = new Security();
            security.setSecurityNo(0);
            Provider provider = providerDao.getProvider(providerNo);
            loggedInInfo.setLoggedInSecurity(security);
            loggedInInfo.setLoggedInProvider(provider);
            try {
                hlp.manageCDMTicklers(loggedInInfo, providerNo, alertCodes);
            } catch (ShutdownException e) {
                logger.debug("AlertTimer noticed shutdown signaled.");
            } catch (Exception e) {
                logger.error("unexpected error", e);
            } finally {
                DbConnectionFilter.releaseAllThreadDbResources();
            }
        }
    }

}
