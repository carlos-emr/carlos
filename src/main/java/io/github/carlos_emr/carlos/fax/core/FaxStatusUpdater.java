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
package io.github.carlos_emr.carlos.fax.core;

import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FaxStatusUpdater {

    private final FaxJobDao faxJobDao;
    private final FaxConfigDao faxConfigDao;
    private final FaxProviderClientFactory faxProviderClientFactory;
    private Logger log = MiscUtils.getLogger();

    @Autowired
    public FaxStatusUpdater(FaxJobDao faxJobDao, FaxConfigDao faxConfigDao,
            FaxProviderClientFactory faxProviderClientFactory) {
        this.faxJobDao = faxJobDao;
        this.faxConfigDao = faxConfigDao;
        this.faxProviderClientFactory = faxProviderClientFactory;
    }

    public void updateStatus() {

        List<FaxJob> faxJobList = faxJobDao.getInprogressFaxesByJobId();

        log.info("CHECKING STATUS OF " + faxJobList.size() + " FAXES");

        for (FaxJob faxJob : faxJobList) {
            FaxConfig faxConfig = faxConfigDao.getConfigByNumber(faxJob.getFax_line());

            if (faxConfig == null) {
                log.error("Could not find faxConfig while processing fax id: " + faxJob.getId() + " Has the fax number changed?");
            } else if (faxConfig.isActive()) {
                try {
                    FaxProviderClient providerClient = faxProviderClientFactory.getClient(faxConfig);
                    FaxJob faxJobUpdated = providerClient.fetchFaxStatus(faxConfig, faxJob);
                    faxJob.setStatus(faxJobUpdated.getStatus());
                    faxJob.setStatusString(faxJobUpdated.getStatusString());
                    log.info("UPDATED FAX JOB ID " + faxJob.getJobId() + " WITH STATUS " + faxJob.getStatus());
                    faxJobDao.merge(faxJob);
                } catch (FaxProviderException e) {
                    log.error("Failed to update fax status for fax id " + faxJob.getId(), e);
                }
            }

        }
    }

}
