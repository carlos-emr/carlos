/*
 *Copyright (c) 2023. Magenta Health Inc. All Rights Reserved.
 *
 *This software is published under the GPL GNU General Public License.
 *
 *This program is free software; you can redistribute it and/or
 *modify it under the terms of the GNU General Public License
 *as published by the Free Software Foundation; either version 2
 *of the License, or (at your option) any later version.
 *This program is distributed in the hope that it will be useful,
 *but WITHOUT ANY WARRANTY; without even the implied warranty of
 *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *GNU General Public License for more details.
 *
 *You should have received a copy of the GNU General Public License
 *along with this program; if not, write to the Free Software
 *Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 */
package ca.openosp.openo.inboxhub.display;

import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.opensymphony.xwork2.ActionSupport;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.inboxhub.inboxdata.LabDataController;
import ca.openosp.openo.inboxhub.query.InboxhubQuery;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.lab.ca.on.LabResultData;
import ca.openosp.openo.mds.data.CategoryData;

public class ManageInboxhub2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private InboxhubQuery query = new InboxhubQuery();

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Struts action execute function that is called by default
     */
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("displayInboxForm".equals(method)) {
            return displayInboxForm();
        } else if ("displayInboxList".equals(method)) {
            return displayInboxList();
        } else if ("displayInboxView".equals(method)) {
            return displayInboxView();
        }
        return displayInboxForm();
    }

    /**
     * Displays the inbox form, this is the first call when you open the inbox hub jsp
     * @return Struts result name
     */
    public String displayInboxForm() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, null)) {
            return "unauthorized";
        }

        LabDataController labDataController = new LabDataController();
        String unclaimed = (String) request.getParameter("unclaimed");

        labDataController.setInboxFormQueryUnclaimed(query, unclaimed);

        labDataController.sanitizeInboxFormQuery(loggedInInfo, query, null, null);
        CategoryData categoryData = labDataController.getCategoryData(query);
        int[] totalCounts = labDataController.getTotalResultsCountBasedOnQuery(query, categoryData);

        request.setAttribute("totalDocsCount", totalCounts[0]);
        request.setAttribute("totalLabsCount", totalCounts[1]);
        request.setAttribute("totalHRMCount", totalCounts[2]);
        request.setAttribute("totalResultsCount", totalCounts[3]);
        request.setAttribute("categoryData", categoryData);
        return "success";
    }

    /**
     * Displays the inbox list if the page is set to that option
     * fetches all of the lab data to show in the inbox hub list jsp
     * @return Struts result name
     */
    public String displayInboxList() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, null)) {
            return "unauthorized";
        }

        fetchLabData(request);
        return "displayList";
    }

    /**
     * Displays the inbox view if the page is set to that option
     * fetches all of the lab data to show in the inbox hub view jsp
     * @return Struts result name
     */
    public String displayInboxView() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, null)) {
            return "unauthorized";
        }

        fetchLabData(request);
        return "displayView";
    }

    /**
     * Fetches all of the lab data that will be shown onto the inbox hub jsp
     * @param request
     */
    private void fetchLabData(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String page = request.getParameter("page");
        String pageSize = request.getParameter("pageSize");
        String demographicFilter = request.getParameter("demographicFilter");
        String typeFilter = request.getParameter("typeFilter");

        int defaultPage = 1;
        int defaultPageSize = 20;
        int pageNum;
        int pageSizeNum;  
        
        try {
            pageNum = Integer.parseInt(page);
        } catch (NumberFormatException e) {
            logger.error("Setting default page number of: " + defaultPage + " due to an error parsing page parameter:", e);
            pageNum = defaultPage;
        }
        try {
            pageSizeNum = Integer.parseInt(pageSize);
        } catch (NumberFormatException e) {
            logger.error("Setting default page size of: " + defaultPageSize + " due to an error parsing pageSize parameter:", e);
            pageSizeNum = defaultPageSize;
        }

        query.setPage(pageNum);
        query.setPageSize(pageSizeNum);

        LabDataController labDataController = new LabDataController();
        labDataController.sanitizeInboxFormQuery(loggedInInfo, query, demographicFilter, typeFilter);
        ArrayList<LabResultData> labDocs = labDataController.getLabData(loggedInInfo, query);
        if (labDocs.size() > 0) {
            String providerNo = request.getSession().getAttribute("user").toString();
            ArrayList<String> labLinks = labDataController.getLabLink(labDocs, query, request.getContextPath(), providerNo);
            request.setAttribute("labLinks", labLinks);
        }

        request.setAttribute("page", page);
        request.setAttribute("pageSize", pageSize);
        request.setAttribute("labDocs", labDocs);
        request.setAttribute("hasMoreData", labDocs.size() > 0);
        request.setAttribute("searchProviderNo", query.getSearchProviderNo());
    }

    public InboxhubQuery getQuery() {
        return query;
    }

    public void setQuery(InboxhubQuery query) {
        this.query = query;
    }
}