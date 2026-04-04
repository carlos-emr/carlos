/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
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
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.dashboard.handler;

import java.util.Date;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class DiseaseRegistryHandler {

    private static Logger logger = MiscUtils.getLogger();
    private LoggedInInfo loggedInInfo;

    // XXX is there an API for getting this reliably? (other than `new Icd9().getCodingSystem()`)
    private static final String ICD9_CODING_SYSTEM = "icd9";

    private DxresearchDAO dao = (DxresearchDAO) SpringUtils.getBean(DxresearchDAO.class);

    public String getDescription(String icd9code) {
        return dao.getDescription(ICD9_CODING_SYSTEM, icd9code);
    }

    public void addToDiseaseRegistry(int demographicNo, String icd9code) {
        addToDiseaseRegistry(demographicNo, icd9code, getProviderNo());
    }

    public Integer addToDiseaseRegistry(int demographicNo, String icd9code, String providerNo) {
        boolean activeEntryExists = dao.activeEntryExists(demographicNo, ICD9_CODING_SYSTEM, icd9code);
        if (activeEntryExists) {
            logger.info("Patient already has active entry for code {} in disease registry", LogSanitizer.sanitize(icd9code));
            return null;
        }

        Dxresearch dx = new Dxresearch();
        dx.setStartDate(new Date());
        dx.setCodingSystem(ICD9_CODING_SYSTEM);
        dx.setDemographicNo(demographicNo);
        dx.setDxresearchCode(icd9code);
        dx.setStatus('A');
        dx.setProviderNo(providerNo);

        dao.persist(dx);

        logger.info("Added disease registry entry (codingSystem={}, dxId={})", ICD9_CODING_SYSTEM, dx.getId());
        return dx.getId();
    }

    protected LoggedInInfo getLoggedInInfo() {
        return loggedInInfo;
    }

    public void setLoggedInInfo(LoggedInInfo loggedInInfo) {
        this.loggedInInfo = loggedInInfo;
    }

    private String getProviderNo() {
        String providerNo = null;
        if (loggedInInfo != null) {
            providerNo = getLoggedInInfo().getLoggedInProviderNo();
        }
        return providerNo;
    }

}
