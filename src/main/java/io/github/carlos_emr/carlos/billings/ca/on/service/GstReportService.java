/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
/*
 * GstReportService.java
 *
 * Created on August 15, 2007, 5:20 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.Properties;
import java.util.ArrayList;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.util.ConversionUtils;
/**
 * CARLOS EMR component for {@code GstReportService}.
 *
 * <p>Keep this class focused on its Java-layer responsibility and avoid moving
 * request or rendering behavior back into JSP scriptlets.</p>
 */

@org.springframework.stereotype.Service
/**
 * Business logic service calculating and aggregating GST for taxable private services.
 * Generates the necessary financial reports for tax compliance and remittance.
 */
public class GstReportService {

    private final BillingONExtDao dao;
    private final DemographicManager demographicManager;
    private final BillingServiceDao billingServiceDao;

    public GstReportService(BillingONExtDao dao,
                     DemographicManager demographicManager,
                     BillingServiceDao billingServiceDao) {
        this.dao = dao;
        this.demographicManager = demographicManager;
        this.billingServiceDao = billingServiceDao;
    }

    public ArrayList<Properties> getGST(LoggedInInfo loggedInInfo, String providerNo, String startDate, String endDate) {
        Properties props;
        ArrayList<String> billno = new ArrayList<String>();
        ArrayList<Properties> list = new ArrayList<Properties>();
        // First find all the billing_no referring to the selected provider_no.
        for (BillingONExt e : dao.find("provider_no", providerNo)) {
            billno.add("" + e.getBillingNo());
        }

        // For every bill the providers is involved with, search the gst value, date, demo no within the chosen dates
        for (int i = 0; i < billno.size(); i++) {
            for (BillingONExt e : dao.find(ConversionUtils.fromIntString(billno.get(i)), "gst", ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate))) {
                props = new Properties();
                props.setProperty("gst", e.getValue());
                props.setProperty("date", ConversionUtils.toDateString(e.getDateTime()));
                props.setProperty("demographic_no", "" + e.getDemographicNo());

                Demographic demo = demographicManager.getDemographic(loggedInInfo, e.getDemographicNo());
                if (demo != null) {
                    props.setProperty("name", demo.getFirstName() + " " + demo.getLastName());
                }

                for (BillingONExt ee : dao.find(ConversionUtils.fromIntString(billno.get(i)), "total", ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate))) {
                    props.setProperty("total", ee.getValue());
                }
                list.add(props);
            }
        }
        return list;
    }

    public String getGstFlag(String code, String date) {
        for (BillingService bs : billingServiceDao.findGst(code, ConversionUtils.fromDateString(date))) {
            return ConversionUtils.toBoolString(bs.getGstFlag());
        }
        return "";
    }
}
