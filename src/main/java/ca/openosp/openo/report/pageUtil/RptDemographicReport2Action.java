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


package ca.openosp.openo.report.pageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.report.data.DemographicSets;
import ca.openosp.openo.report.data.RptDemographicQuery2Builder;
import ca.openosp.openo.report.data.RptDemographicQuery2Loader;
import ca.openosp.openo.report.data.RptDemographicQuery2Saver;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;

import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.ModelDriven;

import org.apache.struts2.ServletActionContext;

public class RptDemographicReport2Action extends ActionSupport implements ModelDriven<RptDemographicReport2Form> {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private RptDemographicReport2Form form = new RptDemographicReport2Form();

    @Override
    public RptDemographicReport2Form getModel() {
        return form;
    }

    public String execute() throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String query = form.getQuery();
        String[] select = form.getSelect();
        String studyId = form.getStudyId();

        // Always pass the form to the JSP for repopulating field values
        request.setAttribute("formBean", form);

        if ("Run Query".equals(query)) {
            MiscUtils.getLogger().debug("run query");
            RptDemographicQuery2Builder demoQ = new RptDemographicQuery2Builder();
            ArrayList searchedArray = demoQ.buildQuery(loggedInInfo, form);
            MiscUtils.getLogger().debug("searchArray size " + searchedArray.size());
            request.setAttribute("searchedArray", searchedArray);
            request.setAttribute("selectArray", select);
            request.setAttribute("studyId", studyId);
        } else if ("Save Query".equals(query)) {
            RptDemographicQuery2Saver demoS = new RptDemographicQuery2Saver();
            demoS.saveQuery(form);
        } else if ("Load Query".equals(query)) {
            RptDemographicQuery2Loader demoL = new RptDemographicQuery2Loader();
            RptDemographicReport2Form dRF = demoL.queryLoader(form);
            request.setAttribute("formBean", dRF);
        } else if ("Add to Study".equals(query)) {
            RptDemographicQuery2Builder demoQ = new RptDemographicQuery2Builder();
            ArrayList searchedArray = demoQ.buildQuery(loggedInInfo, form);
            request.setAttribute("searchedArray", searchedArray);
            MiscUtils.getLogger().info("SELECT ARRAY IS NULL " + String.valueOf(select == null));
            MiscUtils.getLogger().info("STUDY ID IS " + studyId);
            request.setAttribute("selectArray", select);
            request.setAttribute("studyId", studyId);
            return "addToStudy";
        } else if ("Run Query And Save to Patient Set".equals(query)) {
            MiscUtils.getLogger().debug("run query and save to patient set");
            RptDemographicQuery2Builder demoQ = new RptDemographicQuery2Builder();
            ArrayList searchedArray = demoQ.buildQuery(loggedInInfo, form);

            if (select != null && select.length > 0 && select[0].equals("demographic_no")) {
                DemographicSets demoSet = new DemographicSets();


                List<String> theDemos = new ArrayList<String>();
                for (int x = 0; x < searchedArray.size(); x++) {
                    ArrayList<String> row = (ArrayList<String>) searchedArray.get(x);
                    theDemos.add(row.get(0));
                }
                demoSet.addDemographicSet(form.getSetName(), theDemos);
            }

            MiscUtils.getLogger().debug("searchArray size " + searchedArray.size());
            request.setAttribute("searchedArray", searchedArray);
            request.setAttribute("selectArray", select);
            request.setAttribute("studyId", studyId);
        }

        return SUCCESS;
    }
}
