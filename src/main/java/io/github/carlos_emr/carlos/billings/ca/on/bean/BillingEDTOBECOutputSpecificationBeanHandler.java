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

package io.github.carlos_emr.carlos.billings.ca.on.bean;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.BatchEligibilityDao;
import io.github.carlos_emr.carlos.commn.model.BatchEligibility;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class BillingEDTOBECOutputSpecificationBeanHandler {

    private BatchEligibilityDao batchEligibilityDao = (BatchEligibilityDao) SpringUtils.getBean(BatchEligibilityDao.class);

    Vector<BillingEDTOBECOutputSpecificationBean> EDTOBECOutputSecifiationBeanVector = new Vector<BillingEDTOBECOutputSpecificationBean>();
    public boolean verdict = true;

    public BillingEDTOBECOutputSpecificationBeanHandler(LoggedInInfo loggedInInfo, FileInputStream file) {
        init(loggedInInfo, file);
    }

    public boolean init(LoggedInInfo loggedInInfo, FileInputStream file) {

        InputStreamReader reader = new InputStreamReader(file);
        BufferedReader input = new BufferedReader(reader);
        String nextline;

        try {

            while ((nextline = input.readLine()) != null) {

                if (nextline.length() > 2) {

                    String obecHIN = nextline.substring(0, 10);
                    String obecVer = nextline.substring(10, 12);
                    String obecResponse = nextline.substring(12, 14);
                    BillingEDTOBECOutputSpecificationBean osBean = new BillingEDTOBECOutputSpecificationBean(obecHIN, obecVer, obecResponse);

                    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
                    List<Demographic> demos = demographicManager.searchByHealthCard(loggedInInfo, obecHIN);
                    if (!demos.isEmpty()) {
                        Demographic demo = demos.get(0);
                        osBean.setLastName(demo.getLastName());
                        osBean.setFirstName(demo.getFirstName());
                        osBean.setDOB(demo.getDateOfBirth());
                        osBean.setSex(demo.getSex());

                        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
                        Provider provider = providerDao.getProvider(StringUtils.trimToNull(demo.getProviderNo()));

                        if (provider != null) {
                            osBean.setIdentifier(provider.getLastName());
                        }
                    }
                    BatchEligibility batchEligibility = batchEligibilityDao.find(Integer.parseInt(obecResponse));

                    if (batchEligibility != null) {
                        osBean.setMOH(batchEligibility.getMOHResponse());
                    }

                    if (nextline.length() > 14) {
                        osBean.setExpiry(nextline.substring(27, 35));
                        osBean.setSecondName(nextline.substring(85, 105));
                    }

                    EDTOBECOutputSecifiationBeanVector.add(osBean);
                }
            }
        } catch (IOException ioe) {
            MiscUtils.getLogger().error("Error", ioe);
        } catch (StringIndexOutOfBoundsException ioe) {
            verdict = false;
        }
        return verdict;
    }

    public Vector<BillingEDTOBECOutputSpecificationBean> getEDTOBECOutputSecifiationBeanVector() {
        return EDTOBECOutputSecifiationBeanVector;
    }

}
