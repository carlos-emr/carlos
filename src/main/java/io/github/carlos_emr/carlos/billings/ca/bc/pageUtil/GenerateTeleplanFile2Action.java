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


package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.TeleplanFileWriter;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.TeleplanSubmission;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillActivityDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.billings.ca.on.web.BillingRequestGuards;
import io.github.carlos_emr.carlos.providers.data.ProviderData;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Generate Teleplan File: the page that writes the MSP claim file for the chosen billing physicians (or all of
 * them) into HOME_DIR, records the batch in billactivity, marks the claims billed and lists earlier batches.
 *
 * <p>Opening the page (the Administration menu item, the year archive links) is a GET with no {@code providers}
 * and only renders it. Generating is the page's Create Report form, a POST naming {@code providers}; a GET or HEAD
 * naming them is refused with 405 before anything is read or written, because the menu GET used to write a batch
 * of its own (issue #4439 D3).</p>
 *
 * @author jay
 */
public class GenerateTeleplanFile2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    /**
     * Creates a new instance of GenerateTeleplanFile2Action
     */
    public GenerateTeleplanFile2Action() {
    }

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // A plain GET or HEAD only opens the page and its Activity List; anything else is a request to generate.
        String method = request.getMethod();
        boolean readOnly = ("GET".equals(method) || "HEAD".equals(method)) && request.getParameter("providers") == null;
        if (readOnly) {
            return SUCCESS;
        }
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }
        if (StringUtils.isBlank(request.getParameter("providers"))) {
            // With no physician chosen the lookup matches none, and the commit still wrote an empty batch.
            request.setAttribute("error", "Select a provider to generate the Teleplan file.");
            return SUCCESS;
        }

        String home_dir = CarlosProperties.getInstance().getProperty("HOME_DIR");
        String dataCenterId = CarlosProperties.getInstance().getProperty("dataCenterId");

        String batchCount = "0";
        String providerNo = request.getParameter("user");
        String provider = request.getParameter("providers");
        String providerBillingNo = request.getParameter("providers");
        if (provider != null && provider.equals("all")) {
            providerBillingNo = "%";
        }
        ProviderData pd = new ProviderData();
        List list = pd.getProviderListWithInsuranceNo(providerBillingNo);

        ProviderData[] pdArr = new ProviderData[list.size()];

        for (int i = 0; i < list.size(); i++) {
            String provNo = (String) list.get(i);
            pdArr[i] = new ProviderData(provNo);
        }

        //This needs to be replaced for sim
        boolean testRun = false;
        //To prevent multiple submissions being generated at the same time
        synchronized (this) {
            try {

                BillingmasterDAO billingmasterDAO = (BillingmasterDAO) SpringUtils.getBean(BillingmasterDAO.class);
                DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
                TeleplanFileWriter teleplanWr = new TeleplanFileWriter();
                teleplanWr.setBillingmasterDAO(billingmasterDAO);
                teleplanWr.setDemographicManager(demographicManager);
                TeleplanSubmission submission = teleplanWr.getSubmission(LoggedInInfo.getLoggedInInfoFromSession(request), testRun, pdArr, dataCenterId);

                BillActivityDAO bActDao = new BillActivityDAO();
                batchCount = bActDao.getNextMonthlySequence("");

                // -commit the record to Bill Activity
                //Create Filename
                String filename = "H" + bActDao.getMonthCode() + UtilDateUtilities.getToday("yyMMdd_HHmmss") + "_" + Misc.forwardZero(batchCount, 3);

                MiscUtils.getLogger().debug("filename: " + filename + " home_dir " + home_dir);
                MiscUtils.getLogger().debug(submission.toString());
            
            
            /*
              -Saves the html and msp file
              -commits updated for billing, billingmaster
              -commits log statements
              -commits Submission Links
             */
                //submission.commit(filename,home_dir);
                submission.commit(filename, home_dir, bActDao.getMonthCode(), batchCount, providerNo);

            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                request.setAttribute("error", e.getMessage());
            }
        }
        return SUCCESS;
    }
}
/*
 // TODO:
 
 *-should i create a BillingDAO object? i think so
 *-should i create a billactivity primary key? i think so done
 */
