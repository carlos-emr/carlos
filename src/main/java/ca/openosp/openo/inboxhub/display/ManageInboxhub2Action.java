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

/**
 * Struts2 action for managing the Inbox Hub interface in the OpenO EMR system.
 * <p>
 * The Inbox Hub provides healthcare providers with a centralized interface to view and manage
 * incoming medical results including laboratory reports, clinical documents, and Hospital Report
 * Manager (HRM) data. This action handles three primary display modes: form view (summary counts),
 * list view (paginated results), and detailed view (expanded results display).
 * </p>
 * <p>
 * This action follows the 2Action naming convention, indicating it is part of the Struts 1.x to
 * Struts 2.x migration pattern. It uses method-based routing where the "method" request parameter
 * determines which display mode to invoke.
 * </p>
 * <p>
 * <strong>Security:</strong> All methods enforce read-level access to the "_lab" security object
 * using {@link SecurityInfoManager}. Unauthorized access returns the "unauthorized" result.
 * </p>
 * <p>
 * <strong>Healthcare Context:</strong> The inbox hub is critical for clinical workflow, allowing
 * providers to review lab results, diagnostic reports, and hospital communications. Results can
 * be filtered by provider, patient demographics, and result type (lab/doc/HRM). The action supports
 * both claimed results (assigned to specific providers) and unclaimed results (available to all).
 * </p>
 *
 * @see ca.openosp.openo.inboxhub.inboxdata.LabDataController
 * @see ca.openosp.openo.inboxhub.query.InboxhubQuery
 * @see ca.openosp.openo.lab.ca.on.LabResultData
 * @see ca.openosp.openo.managers.SecurityInfoManager
 * @since 2026-01-24
 */
public class ManageInboxhub2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private InboxhubQuery query = new InboxhubQuery();

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Main Struts2 action execution method with method-based routing.
     * <p>
     * Routes requests to the appropriate display method based on the "method" request parameter.
     * Supported methods include:
     * </p>
     * <ul>
     *   <li><code>displayInboxForm</code> - Shows summary view with result counts</li>
     *   <li><code>displayInboxList</code> - Shows paginated list of results</li>
     *   <li><code>displayInboxView</code> - Shows detailed view of results</li>
     * </ul>
     * <p>
     * If no method parameter is provided or the method is unrecognized, defaults to
     * {@link #displayInboxForm()}.
     * </p>
     *
     * @return String Struts2 result name ("success", "displayList", "displayView", or "unauthorized")
     * @throws Exception if an error occurs during method execution
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
     * Displays the inbox form summary view with aggregate result counts.
     * <p>
     * This is typically the initial view when opening the inbox hub interface. It retrieves
     * and displays summary statistics for all result types (documents, labs, HRM) based on
     * the current query filters. The method supports filtering by unclaimed results when the
     * "unclaimed" request parameter is set.
     * </p>
     * <p>
     * The method performs the following operations:
     * </p>
     * <ol>
     *   <li>Validates user has read access to lab results via SecurityInfoManager</li>
     *   <li>Initializes query with unclaimed filter if requested</li>
     *   <li>Sanitizes query parameters based on user context and permissions</li>
     *   <li>Retrieves category data and aggregate counts for each result type</li>
     *   <li>Sets request attributes for JSP rendering</li>
     * </ol>
     * <p>
     * <strong>Security:</strong> Requires "_lab" read privilege. Returns "unauthorized" if access denied.
     * </p>
     *
     * @return String "success" if authorized and data retrieved successfully, "unauthorized" if access denied
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
     * Displays the inbox results in a paginated list format.
     * <p>
     * This method retrieves and displays laboratory results, documents, and HRM data in a
     * list view with pagination support. It delegates to {@link #fetchLabData(HttpServletRequest)}
     * to retrieve the actual result data based on current query parameters including page number,
     * page size, demographic filters, and type filters.
     * </p>
     * <p>
     * <strong>Security:</strong> Requires "_lab" read privilege. Returns "unauthorized" if access denied.
     * </p>
     *
     * @return String "displayList" if authorized and data retrieved successfully, "unauthorized" if access denied
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
     * Displays the inbox results in a detailed view format.
     * <p>
     * This method retrieves and displays laboratory results, documents, and HRM data in a
     * more detailed view with enhanced result information. Like {@link #displayInboxList()},
     * it delegates to {@link #fetchLabData(HttpServletRequest)} to retrieve result data based
     * on current query parameters.
     * </p>
     * <p>
     * <strong>Security:</strong> Requires "_lab" read privilege. Returns "unauthorized" if access denied.
     * </p>
     *
     * @return String "displayView" if authorized and data retrieved successfully, "unauthorized" if access denied
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
     * Retrieves laboratory and medical result data for display in the inbox hub interface.
     * <p>
     * This private helper method performs the following operations:
     * </p>
     * <ol>
     *   <li>Extracts and validates pagination parameters (page, pageSize) from the request</li>
     *   <li>Extracts filter parameters (demographicFilter, typeFilter) from the request</li>
     *   <li>Sanitizes the inbox query based on user context and filters</li>
     *   <li>Retrieves lab result data matching the query criteria</li>
     *   <li>Generates navigation links for each lab result</li>
     *   <li>Sets request attributes for JSP rendering</li>
     * </ol>
     * <p>
     * <strong>Error Handling:</strong> Invalid pagination parameters default to page 1 and page size 20.
     * NumberFormatException errors are logged but do not halt execution.
     * </p>
     * <p>
     * <strong>Request Attributes Set:</strong>
     * </p>
     * <ul>
     *   <li><code>page</code> - String current page number</li>
     *   <li><code>pageSize</code> - String number of results per page</li>
     *   <li><code>labDocs</code> - ArrayList&lt;LabResultData&gt; the result data</li>
     *   <li><code>labLinks</code> - ArrayList&lt;String&gt; navigation links for results (if results exist)</li>
     *   <li><code>hasMoreData</code> - Boolean whether more results are available</li>
     *   <li><code>searchProviderNo</code> - String provider number from query</li>
     * </ul>
     *
     * @param request HttpServletRequest the servlet request containing query parameters and session data
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

    /**
     * Retrieves the current inbox hub query object.
     * <p>
     * This getter is used by the Struts2 framework for accessing the query object,
     * which contains all filter and pagination settings for the inbox hub.
     * </p>
     *
     * @return InboxhubQuery the current query object containing filter and pagination settings
     */
    public InboxhubQuery getQuery() {
        return query;
    }

    /**
     * Sets the inbox hub query object.
     * <p>
     * This setter is used by the Struts2 framework for dependency injection and
     * parameter binding. It allows external configuration of the query object.
     * </p>
     *
     * @param query InboxhubQuery the query object to set with filter and pagination settings
     */
    public void setQuery(InboxhubQuery query) {
        this.query = query;
    }
}