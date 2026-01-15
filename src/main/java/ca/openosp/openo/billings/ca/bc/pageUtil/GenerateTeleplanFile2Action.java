//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.billings.ca.bc.pageUtil;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.Misc;
import ca.openosp.OscarProperties;
import ca.openosp.openo.billings.ca.bc.MSP.TeleplanFileWriter;
import ca.openosp.openo.billings.ca.bc.MSP.TeleplanSubmission;
import ca.openosp.openo.billings.ca.bc.data.BillActivityDAO;
import ca.openosp.openo.billings.ca.bc.data.BillingmasterDAO;
import ca.openosp.openo.providers.data.ProviderData;
import ca.openosp.openo.util.UtilDateUtilities;


/**
 * Goals
 * + Take file generation logic out of jsp page
 *
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class GenerateTeleplanFile2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    /**
     * Creates a new instance of GenerateTeleplanFile2Action
     */
    public GenerateTeleplanFile2Action() {
    }

    public String execute() throws Exception {
        MiscUtils.getLogger().debug("SimulateTeleplanAction2 action jackson");

        String home_dir = OscarProperties.getInstance().getProperty("HOME_DIR");
        String dataCenterId = OscarProperties.getInstance().getProperty("dataCenterId");

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
