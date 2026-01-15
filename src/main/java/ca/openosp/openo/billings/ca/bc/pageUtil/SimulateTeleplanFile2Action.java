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

import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.billings.ca.bc.MSP.TeleplanFileWriter;
import ca.openosp.openo.billings.ca.bc.MSP.TeleplanSubmission;
import ca.openosp.openo.billings.ca.bc.data.BillingmasterDAO;
import ca.openosp.openo.providers.data.ProviderData;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Action Simulates a MSP teleplan file but doesn't commit any of the data.
 *
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class SimulateTeleplanFile2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    /**
     * Creates a new instance of GenerateTeleplanFile2Action
     */
    public SimulateTeleplanFile2Action() {
    }

    public String execute() throws Exception {

        String dataCenterId = OscarProperties.getInstance().getProperty("dataCenterId");

        String provider = request.getParameter("providers");
        String providerBillingNo = request.getParameter("providers");
        if (provider != null && provider.equals("all")) {
            providerBillingNo = "%";
        }
        @SuppressWarnings("deprecation")
        ProviderData pd = new ProviderData();

        @SuppressWarnings("deprecation")
        List<String> list = pd.getProviderListWithInsuranceNo(providerBillingNo);

        @SuppressWarnings("deprecation")
        ProviderData[] pdArr = new ProviderData[list.size()];

        for (int i = 0; i < list.size(); i++) {
            String provNo = list.get(i);
            pdArr[i] = new ProviderData(provNo);
        }
        //This needs to be replaced for sim
        boolean testRun = true;
        //To prevent multiple submissions being generated at the same time
        synchronized (this) {
            try {
                BillingmasterDAO billingmasterDAO = SpringUtils.getBean(BillingmasterDAO.class);
                DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

                TeleplanFileWriter teleplanWr = new TeleplanFileWriter();
                teleplanWr.setBillingmasterDAO(billingmasterDAO);
                teleplanWr.setDemographicManager(demographicManager);
                TeleplanSubmission submission = teleplanWr.getSubmission(LoggedInInfo.getLoggedInInfoFromSession(request), testRun, pdArr, dataCenterId);
                request.setAttribute("TeleplanHtmlFile", submission.getHtmlFile());

            } catch (Exception e) {
                MiscUtils.getLogger().debug("Error: Teleplan Html File", e);
            }
        }
        return SUCCESS;
    }
}
